module H = Hashtbl
module IH = Inthash
module AL = Alpha

(* Globals that have already been defined. Indexed by the variable name. *)
let alreadyDefined: (string, location) H.t = H.create 117

(* Globals that were created due to static local variables. We chose their 
 * names to be distinct from any global encountered at the time. But we might 
 * see a global with conflicting name later in the file. *)
let staticLocals: (string, varinfo) H.t = H.create 13


(* Typedefs. We chose their names to be distinct from any global encounterd 
 * at the time. But we might see a global with conflicting name later in the 
 * file *)
let typedefs: (string, typeinfo) H.t = H.create 13

(********* ENVIRONMENTS ***************)

(* The environment is kept in two distinct data structures. A hash table maps
 * each original variable name into a varinfo (for variables, or an
 * enumeration tag, or a type). (Note that the varinfo might contain an
 * alpha-converted name different from that of the lookup name.) The Ocaml
 * hash tables can keep multiple mappings for a single key. Each time the
 * last mapping is returned and upon deletion the old mapping is restored. To
 * keep track of local scopes we also maintain a list of scopes (represented
 * as lists).  *)
type envdata =
    EnvVar of varinfo                   (* The name refers to a variable
                                         * (which could also be a function) *)
  | EnvEnum of exp * typ                (* The name refers to an enumeration
                                         * tag for which we know the value
                                         * and the host type *)
  | EnvTyp of typ                       (* The name is of the form  "struct
                                         * foo", or "union foo" or "enum foo"
                                         * and refers to a type. Note that
                                         * the name of the actual type might
                                         * be different from foo due to alpha
                                         * conversion *)
  | EnvLabel of string                  (* The name refers to a label. This 
                                         * is useful for GCC's locally 
                                         * declared labels. The lookup name 
                                         * for this category is "label foo" *)

let env : (string, envdata * location) H.t = H.create 307
(* We also keep a global environment. This is always a subset of the env *)
let genv : (string, envdata * location) H.t = H.create 307

 (* In the scope we keep the original name, so we can remove them from the
  * hash table easily *)
type undoScope =
    UndoRemoveFromEnv of string
  | UndoResetAlphaCounter of cabsloc AL.alphaTableData ref * 
                             cabsloc AL.alphaTableData
  | UndoRemoveFromAlphaTable of string

let scopes :  undoScope list ref list ref = ref []

let isAtTopLevel () = 
  !scopes = []


(* When you add to env, you also add it to the current scope *)
let addLocalToEnv (n: string) (d: envdata) = 
  H.add env n (d, !currentLoc);
    (* If we are in a scope, then it means we are not at top level. Add the 
     * name to the scope *)
  (match !scopes with
    [] -> begin
      match d with
        EnvVar _ -> 
          E.s (E.bug "addLocalToEnv: not in a scope when adding %s!" n)
      | _ -> () (* We might add types *)
    end
  | s :: _ -> 
      s := (UndoRemoveFromEnv n) :: !s)


let addGlobalToEnv (k: string) (d: envdata) : unit = 
(*  ignore (E.log "%a: adding global %s to env\n" d_loc !currentLoc k); *)
  H.add env k (d, !currentLoc);
  (* Also add it to the global environment *)
  H.add genv k (d, !currentLoc)
  
  

(* Create a new name based on a given name. The new name is formed from a 
 * prefix (obtained from the given name as the longest prefix that ends with 
 * a non-digit), followed by a '_' and then by a positive integer suffix. The 
 * first argument is a table mapping name prefixes with the largest suffix 
 * used so far for that prefix. The largest suffix is one when only the 
 * version without suffix has been used. *)
let alphaTable : (string, location AL.alphaTableData ref) H.t = H.create 307 
        (* vars and enum tags. For composite types we have names like "struct 
         * foo" or "union bar" *)

(* To keep different name scopes different, we add prefixes to names 
 * specifying the kind of name: the kind can be one of "" for variables or 
 * enum tags, "struct" for structures and unions (they share the name space), 
 * "enum" for enumerations, or "type" for types *)
let kindPlusName (kind: string)
                 (origname: string) : string =
  if kind = "" then origname else
  kind ^ " " ^ origname
                

let stripKind (kind: string) (kindplusname: string) : string = 
  let l = 1 + String.length kind in
  if l > 1 then 
    String.sub kindplusname l (String.length kindplusname - l)
  else
    kindplusname
   
let newAlphaName (globalscope: bool) (* The name should have global scope *)
                 (kind: string) 
                 (origname: string) : string * location = 
  let lookupname = kindPlusName kind origname in
  (* If we are in a scope then it means that we are alpha-converting a local 
   * name. Go and add stuff to reset the state of the alpha table but only to 
   * the top-most scope (that of the enclosing function) *)
  let rec findEnclosingFun = function
      [] -> (* At global scope *)()
    | [s] -> begin
        let prefix = AL.getAlphaPrefix lookupname in
        try
          let countref = H.find alphaTable prefix in
          s := (UndoResetAlphaCounter (countref, !countref)) :: !s
        with Not_found ->
          s := (UndoRemoveFromAlphaTable prefix) :: !s
    end
    | _ :: rest -> findEnclosingFun rest
  in
  if not globalscope then 
    findEnclosingFun !scopes;
  let newname, oldloc = 
           AL.newAlphaName alphaTable None lookupname !currentLoc in
  stripKind kind newname, oldloc
  
(*** When we do statements we need to know the current return type *)
let currentReturnType : typ ref = ref (TVoid([]))
let currentFunctionFDEC: fundec ref = ref dummyFunDec

let lastStructId = ref 0
let anonStructName (k: string) (suggested: string) = 
  incr lastStructId;
  "__anon" ^ k ^ (if suggested <> "" then "_"  ^ suggested else "") 
  ^ "_" ^ (string_of_int (!lastStructId))
  
let constrExprId = ref 0


let startFile () = 
  H.clear env;
  H.clear genv;
  H.clear alphaTable;
  lastStructId := 0



let enterScope () = 
  scopes := (ref []) :: !scopes

     (* Exit a scope and clean the environment. We do not yet delete from 
      * the name table *)
let exitScope () = 
  let this, rest = 
    match !scopes with
      car :: cdr -> car, cdr
    | [] -> E.s (error "Not in a scope")
  in
  scopes := rest;
  let rec loop = function
      [] -> ()
    | UndoRemoveFromEnv n :: t -> 
        H.remove env n; loop t
    | UndoRemoveFromAlphaTable n :: t -> H.remove alphaTable n; loop t
    | UndoResetAlphaCounter (vref, oldv) :: t -> 
        vref := oldv;
        loop t
  in
  loop !this

(* Lookup a variable name. Return also the location of the definition. Might 
 * raise Not_found  *)
let lookupVar (n: string) : varinfo * location = 
  match H.find env n with
    (EnvVar vi), loc -> vi, loc
  | _ -> raise Not_found
        

let lookupGlobalVar (n: string) : varinfo * location = 
  match H.find genv n with
    (EnvVar vi), loc -> vi, loc
  | _ -> raise Not_found
        
let docEnv () = 
  let acc : (string * (envdata * location)) list ref = ref [] in
  let doone () = function
      EnvVar vi, l -> 
        dprintf "Var(%s,global=%b) (at %a)" vi.vname vi.vglob d_loc l
    | EnvEnum (tag, typ), l -> dprintf "Enum (at %a)" d_loc l
    | EnvTyp t, l -> text "typ"
    | EnvLabel l, _ -> text ("label " ^ l)
  in
  H.iter (fun k d -> acc := (k, d) :: !acc) env;
  docList ~sep:line (fun (k, d) -> dprintf "  %s -> %a" k doone d) () !acc



(* Add a new variable. Do alpha-conversion if necessary *)
let alphaConvertVarAndAddToEnv (addtoenv: bool) (vi: varinfo) : varinfo = 
(*
  ignore (E.log "%t: alphaConvert(addtoenv=%b) %s" d_thisloc addtoenv vi.vname);
*)
  (* Announce the name to the alpha conversion table *)
  let newname, oldloc = newAlphaName (addtoenv && vi.vglob) "" vi.vname in
  (* Make a copy of the vi if the name has changed. Never change the name for 
   * global variables *)
  let newvi = 
    if vi.vname = newname then 
      vi 
    else begin
      if vi.vglob then begin
        (* Perhaps this is because we have seen a static local which happened 
         * to get the name that we later want to use for a global. *)
        try 
          let static_local_vi = H.find staticLocals vi.vname in
          H.remove staticLocals vi.vname;
          (* Use the new name for the static local *)
          static_local_vi.vname <- newname;
          (* And continue using the last one *)
          vi
        with Not_found -> begin
          (* Or perhaps we have seen a typedef which stole our name. This is 
           possible because typedefs use the same name space *)
          try
            let typedef_ti = H.find typedefs vi.vname in 
            H.remove typedefs vi.vname;
            (* Use the new name for the typedef instead *)
            typedef_ti.tname <- newname;
            (* And continue using the last name *)
            vi
          with Not_found -> 
            E.s (E.error "It seems that we would need to rename global %s (to %s) because of previous occurrence at %a" 
                   vi.vname newname d_loc oldloc);
        end
      end else begin 
        (* We have changed the name of a local variable. Can we try to detect 
         * if the other variable was also local in the same scope? Not for 
         * now. *)
        copyVarinfo vi newname
      end
    end
  in
  (* Store all locals in the slocals (in reversed order). We'll reverse them 
   * and take out the formals at the end of the function *)
  if not vi.vglob then
    !currentFunctionFDEC.slocals <- newvi :: !currentFunctionFDEC.slocals;

  (if addtoenv then 
    if vi.vglob then
      addGlobalToEnv vi.vname (EnvVar newvi)
    else
      addLocalToEnv vi.vname (EnvVar newvi));
(*
  ignore (E.log "  new=%s\n" newvi.vname);
*)
(*  ignore (E.log "After adding %s alpha table is: %a\n"
            newvi.vname docAlphaTable alphaTable); *)
  newvi

(* Keep a set of self compinfo for composite types *)
let compInfoNameEnv : (string, compinfo) H.t = H.create 113
let enumInfoNameEnv : (string, enuminfo) H.t = H.create 113


let lookupTypeNoError (kind: string) 
                      (n: string) : typ * location = 
  let kn = kindPlusName kind n in
  match H.find env kn with
    EnvTyp t, l -> t, l
  | _ -> raise Not_found

let lookupType (kind: string) 
               (n: string) : typ * location = 
  try
    lookupTypeNoError kind n
  with Not_found -> 
    E.s (error "Cannot find type %s (kind:%s)\n" n kind)

(* Create the self ref cell and add it to the map. Return also an indication 
 * if this is a new one. *)
let createCompInfo (iss: bool) (n: string) : compinfo * bool = 
  (* Add to the self cell set *)
  let key = (if iss then "struct " else "union ") ^ n in
  try
    H.find compInfoNameEnv key, false (* Only if not already in *)
  with Not_found -> begin
    (* Create a compinfo. This will have "cdefined" false. *)
    let res = mkCompInfo iss n (fun _ -> []) [] in
    H.add compInfoNameEnv key res;
    res, true
  end

(* Create the self ref cell and add it to the map. Return an indication 
 * whether this is a new one. *)
let createEnumInfo (n: string) : enuminfo * bool = 
  (* Add to the self cell set *)
  try
    H.find enumInfoNameEnv n, false (* Only if not already in *)
  with Not_found -> begin
    (* Create a enuminfo *)
    let enum = { ename = n; eitems = []; 
                 eattr = []; ereferenced = false; } in
    H.add enumInfoNameEnv n enum;
    enum, true
  end


   (* kind is either "struct" or "union" or "enum" and n is a name *)
let findCompType (kind: string) (n: string) (a: attributes) = 
  let makeForward () = 
    (* This is a forward reference, either because we have not seen this 
     * struct already or because we want to create a version with different 
     * attributes  *)
    if kind = "enum" then 
      let enum, isnew = createEnumInfo n in
      if isnew then
        cabsPushGlobal (GEnumTagDecl (enum, !currentLoc));
      TEnum (enum, a)
    else 
      let iss = if kind = "struct" then true else false in
      let self, isnew = createCompInfo iss n in
      if isnew then 
        cabsPushGlobal (GCompTagDecl (self, !currentLoc));
      TComp (self, a)
  in
  try
    let old, _ = lookupTypeNoError kind n in (* already defined  *)
    let olda = typeAttrs old in
    if Util.equals olda a then old else makeForward ()
  with Not_found -> makeForward ()
  
let lookupLabel (l: string) = 
  try 
    match H.find env (kindPlusName "label" l) with
      EnvLabel l', _ -> l'
    | _ -> raise Not_found
  with Not_found -> 
    l
(* Create and cache varinfo's for globals. Starts with a varinfo but if the 
 * global has been declared already it might come back with another varinfo. 
 * Returns the varinfo to use (might be the old one), and an indication 
 * whether the variable exists already in the environment *)
let makeGlobalVarinfo (isadef: bool) (vi: varinfo) : varinfo * bool =
  let debug = false in
  try (* See if already defined, in the global environment. We could also 
       * look it up in the whole environment but in that case we might see a 
       * local. This can happen when we declare an extern variable with 
       * global scope but we are in a local scope. *)

    (* We lookup in the environement. If this is extern inline then the name 
     * was already changed to foo__extinline. We lookup with the old name *)
    let lookupname = 
      if vi.vstorage = Static then 
        if Str.string_match extInlineSuffRe vi.vname 0 then 
          Str.matched_group 1 vi.vname
        else
          vi.vname
      else
        vi.vname
    in
    if debug then 
      ignore (E.log "makeGlobalVarinfo isadef=%b vi.vname=%s (lookup = %s)\n"
                isadef vi.vname lookupname);

    (* This may throw an exception Not_found *)
    let oldvi, oldloc = lookupGlobalVar lookupname in
    if debug then
      ignore (E.log "  %s already in the env at loc %a\n" 
                vi.vname d_loc oldloc);
    (* It was already defined. We must reuse the varinfo. But clean up the 
     * storage.  *)
    let newstorage = (** See 6.2.2 *)
      match oldvi.vstorage, vi.vstorage with
        (* Extern and something else is that thing *)
      | Extern, other
      | other, Extern -> other

      | NoStorage, other
      | other, NoStorage ->  other


      | _ ->
	  if vi.vstorage != oldvi.vstorage then
            ignore (warn
		      "Inconsistent storage specification for %s. Previous declaration: %a" 
		      vi.vname d_loc oldloc);
          vi.vstorage
    in
    oldvi.vinline <- oldvi.vinline || vi.vinline;
    oldvi.vstorage <- newstorage;
    (* If the new declaration has a section attribute, remove any
     * preexisting section attribute. This mimics behavior of gcc that is
     * required to compile the Linux kernel properly. *)
    if hasAttribute "section" vi.vattr then
      oldvi.vattr <- dropAttribute "section" oldvi.vattr;
    (* Union the attributes *)
    oldvi.vattr <- cabsAddAttributes oldvi.vattr vi.vattr;
    begin 
      try
        oldvi.vtype <- 
           combineTypes 
             (if isadef then CombineFundef else CombineOther) 
             oldvi.vtype vi.vtype;
      with Failure reason -> 
        ignore (E.log "old type = %a\n" d_plaintype oldvi.vtype);
        ignore (E.log "new type = %a\n" d_plaintype vi.vtype);
        E.s (error "Declaration of %s does not match previous declaration from %a (%s)." 
               vi.vname d_loc oldloc reason)
    end;
      
    (* Found an old one. Keep the location always from the definition *)
    if isadef then begin 
      oldvi.vdecl <- vi.vdecl;
    end;
    oldvi, true
      
  with Not_found -> begin (* A new one.  *)
    if debug then
      ignore (E.log "  %s not in the env already\n" vi.vname);
    (* Announce the name to the alpha conversion table. This will not 
     * actually change the name of the vi. See the definition of 
     * alphaConvertVarAndAddToEnv *)
    alphaConvertVarAndAddToEnv true vi, false
  end 
    

