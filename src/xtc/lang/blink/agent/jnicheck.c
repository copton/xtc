#include <assert.h>
#include <string.h>
#include <jni.h>
#include <jvmti.h>
#include <classfile_constants.h>
#include <stdarg.h>
#include <stdlib.h>
#include "agent.h"
#include "jnicheck.h"
#include "state.h"
#include "common.h"
#include "java_method.h"
#include "hashtable.h"
#include "hashtable_itr.h"

#define MAX_GLOBAL_REF (1024)

#define MAX_JMETHODID_ARGUMENTS (20)
#define MAX_JMETHODID (4096)
#define MAX_METHODID_DESC_LENGTH (1024)

struct bda_local_frame
{
  int sentinel;
  int capacity;
  int count;
  jobject * list;
  struct bda_local_frame * next;
};

struct bda_check_jmethodID_info {
  jmethodID mid;
  jint modifier;
  short is_static;
  short num_arguments;
  int  size_argument_type_list;
  const char * argument_types[MAX_JMETHODID_ARGUMENTS];
  char argument_type_list[MAX_METHODID_DESC_LENGTH];
  char return_type;
  const char *cdesc;
  const char *mname;
  const char *mdesc;
};

struct bda_check_jfieldid_info {
  jclass decl_clazz_gref;
  jfieldID fid;
  jint modifier;
  int mutable_final;
  const char * cdesc;
  const char * fname;
  const char * fdesc; /* "java/lang/String" or "Ljava/lang/String;" */
};


struct hashtable * bda_global_ref_table = NULL;
struct hashtable * bda_weak_global_ref_table = NULL;
struct hashtable * bda_resource_table = NULL;

static int bda_check_jmethodID_set_size = 0;
static struct bda_check_jmethodID_info bda_jmethodID_set[1024];

static int bda_check_jfieldID_set_size = 0;
static struct bda_check_jfieldid_info bda_jfieldID_set[1024];

jclass bda_clazz_string = NULL;
jclass bda_clazz_class = NULL;
jclass bda_clazz_classloader = NULL;
jclass bda_clazz_throwable = NULL;
jclass bda_clazz_booleanArray = NULL;
jclass bda_clazz_byteArray = NULL;
jclass bda_clazz_charArray = NULL;
jclass bda_clazz_shortArray = NULL;
jclass bda_clazz_intArray = NULL;
jclass bda_clazz_longArray = NULL;
jclass bda_clazz_floatArray = NULL;
jclass bda_clazz_doubleArray = NULL;
jclass bda_clazz_field = NULL;
jclass bda_clazz_method = NULL;
jclass bda_clazz_constructor = NULL;
jclass bda_clazz_nio_buffer = NULL;

static int bda_resource_equal(void * k1, void * k2)
{
  return k1 == k2;
}

static unsigned int bda_resource_hash(void * k)
{
    return ((unsigned int)k) % (1 << 20);
}

static jclass bda_ensure_global_clazz(JNIEnv *env, const char * cdesc)
{
  jclass clazz;
  clazz = bda_orig_jni_funcs->FindClass(env, cdesc);
  assert(clazz != NULL);
  clazz = bda_orig_jni_funcs->NewGlobalRef(env, clazz);
  assert(clazz != NULL);
  return clazz;
}

void bda_jnicheck_init(JNIEnv *env)
{
  bda_resource_table = create_hashtable(16, bda_resource_hash, bda_resource_equal);
  bda_global_ref_table = create_hashtable(16, bda_resource_hash, bda_resource_equal);
  bda_weak_global_ref_table = create_hashtable(16, bda_resource_hash, bda_resource_equal);

  bda_clazz_string = bda_ensure_global_clazz(env, "java/lang/String");
  bda_clazz_class = bda_ensure_global_clazz(env, "java/lang/Class");
  bda_clazz_classloader = bda_ensure_global_clazz(env, "java/lang/ClassLoader");
  bda_clazz_throwable = bda_ensure_global_clazz(env, "java/lang/Throwable");
  bda_clazz_booleanArray = bda_ensure_global_clazz(env, "[Z");
  bda_clazz_byteArray = bda_ensure_global_clazz(env, "[B");
  bda_clazz_charArray = bda_ensure_global_clazz(env, "[C");
  bda_clazz_shortArray = bda_ensure_global_clazz(env, "[S");
  bda_clazz_intArray = bda_ensure_global_clazz(env, "[I");
  bda_clazz_longArray = bda_ensure_global_clazz(env, "[J");
  bda_clazz_floatArray = bda_ensure_global_clazz(env, "[F");
  bda_clazz_doubleArray = bda_ensure_global_clazz(env, "[D");
  bda_clazz_field = bda_ensure_global_clazz(env, "java/lang/reflect/Field");
  bda_clazz_method = bda_ensure_global_clazz(env, "java/lang/reflect/Method");
  bda_clazz_constructor = bda_ensure_global_clazz(env, "java/lang/reflect/Constructor");
  bda_clazz_classloader = bda_ensure_global_clazz(env, "java/nio/Buffer");
}
 
static jboolean bda_is_super_class(JNIEnv *env, jclass sup, jclass sub)
{
  jboolean superclass = JNI_FALSE;
  if (bda_orig_jni_funcs->IsSameObject(env, sup, sub)) {
    superclass = JNI_TRUE;
  } else {
    jclass s_class;
    s_class = bda_orig_jni_funcs->GetSuperclass(env, sub);
    while((superclass == JNI_FALSE) && (s_class != NULL)) {
      if (bda_orig_jni_funcs->IsSameObject(env, s_class, sup)) {
        superclass = JNI_TRUE;
      } else {
        jclass tmp;
        tmp = bda_orig_jni_funcs->GetSuperclass(env, s_class);
        bda_orig_jni_funcs->DeleteLocalRef(env, s_class);
        s_class = tmp;
      }
    }      
  }
  return superclass;
}

static struct bda_check_jmethodID_info * bda_check_jmethodID_info_lookup(
  jmethodID mid)
{
  int i = bda_check_jmethodID_set_size;
  for(;i >= 0;i--) {
    if (bda_jmethodID_set[i].mid == mid) {
        return &bda_jmethodID_set[i];
    }
  }
  return NULL;
}

static void bda_check_jmethod_analze_desc(
  const char * desc, void* result, int arg_order, 
  int begin_index, int end_index,
  int num_words, int is_primitive)
{
  struct bda_check_jmethodID_info *s;

  s = (struct bda_check_jmethodID_info*)result;
  assert( !(arg_order == 0) || (s->size_argument_type_list== 0));
  if (arg_order >= 0) {
    int i, desc_begin;
    assert( (s->size_argument_type_list + end_index - begin_index + 2) < MAX_METHODID_DESC_LENGTH);
    desc_begin = s->size_argument_type_list;
    for(i = begin_index; i <= end_index;i++) {
      s-> argument_type_list[s->size_argument_type_list++] = desc[i];
    }
    s->argument_type_list[s->size_argument_type_list++] = '\0';
    s->argument_types[s->num_arguments] = &(s->argument_type_list[desc_begin]);
    s->num_arguments++;
  } else {
    s->return_type = desc[begin_index];
  }
}

void bda_jmethodid_append(jmethodID mid, short is_static, jclass clazz, const char* mname, const char * mdesc)
{
  struct bda_check_jmethodID_info * minfo;

  minfo = bda_check_jmethodID_info_lookup(mid);
  if (minfo == NULL) {
    int nid;
    jint modifier;
    jvmtiError err;
    char * cdesc;

    err = (*bda_jvmti)->GetMethodModifiers(bda_jvmti, mid, &modifier);
    assert (err == JVMTI_ERROR_NONE);
    if (is_static) {
      assert( (modifier & JVM_ACC_STATIC) == JVM_ACC_STATIC);
    } else {
      assert( (modifier & JVM_ACC_STATIC) != JVM_ACC_STATIC);
    }
    err = (*bda_jvmti)->GetClassSignature(bda_jvmti, clazz, &cdesc, NULL);
    assert (err == JVMTI_ERROR_NONE);

    nid = bda_check_jmethodID_set_size++;
    assert(nid < (sizeof(bda_jmethodID_set)/sizeof(struct bda_check_jmethodID_info)));
    minfo = &bda_jmethodID_set[nid];
    bda_parse_method_descriptor(mdesc, minfo,  bda_check_jmethod_analze_desc);
    minfo->is_static = is_static;
    minfo->mid = mid;
    minfo->modifier = modifier;
    minfo->mname = malloc(strlen(mname) + 1);
    strcpy((char*)minfo->mname, mname);
    minfo->mdesc = malloc(strlen(mdesc) + 1);
    strcpy((char*)minfo->mdesc, mdesc);
    minfo->cdesc = malloc(strlen(cdesc) + 1);
    strcpy((char*)minfo->cdesc, cdesc);

    err = (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)cdesc);
    assert (err == JVMTI_ERROR_NONE);    
  }
}

static struct bda_check_jfieldid_info * bda_jfieldID_lookup(
    struct bda_state_info * s, jclass decl_clazz, jfieldID fid, jboolean is_static)
{
  int i = bda_check_jfieldID_set_size;
  for(;i >= 0;i--) {
    struct bda_check_jfieldid_info * finfo = &bda_jfieldID_set[i];
    if (finfo->fid == fid) {
      if (is_static) {
        if (bda_orig_jni_funcs->IsAssignableFrom(s->env, decl_clazz, finfo->decl_clazz_gref)) {
            return finfo;
        }
      } else {
        if (bda_is_super_class(s->env, finfo->decl_clazz_gref, decl_clazz)) {
           return finfo;
        }
      }
    }
  }
  return NULL;
}

static char * bda_str_dup(const char * s)
{
  char * new_s = malloc(strlen(s)+1);
  assert(new_s != NULL);
  strcpy(new_s, s);
  return new_s;
}

static char * bda_str_dup_fdesc_to_cdesc(const char * s)
{
  if (s[0] == 'L') {
    int size = strlen(s)-1;
    char * new_s = malloc(size);
    strncpy(new_s, s+1, size - 1);
    new_s[size-1] = '\0';
    return new_s;
  } else {
    return bda_str_dup(s);
  }
}

void bda_jfieldid_append(struct bda_state_info * s, jfieldID fid, jclass clazz, short is_static, const char* name, const char * desc)
{
  struct bda_check_jfieldid_info *  finfo;
  jvmtiError err;
  jclass decl_clazz;
  int i;

  err = (*bda_jvmti)->GetFieldDeclaringClass(bda_jvmti, clazz, fid, &decl_clazz);
  assert(err == JVMTI_ERROR_NONE);

  /* Search for field information entry. */
  finfo = NULL;
  for(i = bda_check_jfieldID_set_size;i >= 0;i--) {
    struct bda_check_jfieldid_info * f = &bda_jfieldID_set[i];
    if ((f->fid == fid) && (bda_orig_jni_funcs->IsSameObject(s->env, decl_clazz, f->decl_clazz_gref))) {
      finfo = f;
    }
  }

  if (finfo == NULL) {
    int nid;
    jint modifier;
    char * csig;

    err = (*bda_jvmti)->GetFieldModifiers(bda_jvmti, decl_clazz, fid, &modifier);
    assert (err == JVMTI_ERROR_NONE);
    err = (*bda_jvmti)->GetClassSignature(bda_jvmti, decl_clazz, &csig, NULL);
    assert (err == JVMTI_ERROR_NONE);

    nid = bda_check_jfieldID_set_size++;
    assert(nid < (sizeof(bda_jfieldID_set)/sizeof(struct bda_check_jfieldid_info)));
    finfo = &bda_jfieldID_set[nid];
    finfo->decl_clazz_gref = bda_orig_jni_funcs->NewGlobalRef(s->env, decl_clazz);
    assert(finfo->decl_clazz_gref != NULL);
    finfo->cdesc = bda_str_dup_fdesc_to_cdesc(csig);
    finfo->fname = bda_str_dup(name);
    finfo->fdesc = bda_str_dup(desc);
    finfo->fid = fid;
    finfo->modifier = modifier;
    finfo->mutable_final = !strcmp(csig, "Ljava/lang/System;") 
        && (!strcmp(name, "out")||!strcmp(name, "in")||!strcmp(name, "err"));

    err = (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)csig);
    assert (err == JVMTI_ERROR_NONE);    
  }

  bda_orig_jni_funcs->DeleteLocalRef(s->env, decl_clazz);
}


static int bda_check_live(struct bda_state_info *s, jobject o)
{
  struct bda_local_frame * frame;
  int i;

  assert(o != NULL);  

  for (frame = s->local_frame_top; frame != NULL; frame = frame->next){
    for(i = frame->count -1; i >= 0;i--) {
      if (frame->list[i] == o) {
        return 1;
      }
    }
  }

  if ((hashtable_search(bda_global_ref_table, (void*)o) != NULL)
      || (hashtable_search(bda_weak_global_ref_table, (void*)o) != NULL)) {
    return 1;
  }

  if (s->mode == SYS_NATIVE) {return 1;}
  return 0;
}

static int bda_check_assignable_jobject_jclass(struct bda_state_info *s, jobject obj, const char *cdesc, int index, const char * fname)
{
  jclass clazz, source_class;
  jboolean is_assignable;

  clazz = bda_orig_jni_funcs->FindClass(s->env, cdesc);
  assert(clazz != NULL);
  source_class = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
  is_assignable = bda_orig_jni_funcs->IsAssignableFrom(s->env, source_class, clazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, source_class);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, clazz);

  if (!is_assignable) {
      struct bda_state state;
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): %p is not assignable to %p at %d'th to %s\n",
                     s->env, obj, clazz, index, fname);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
  }
  return 1;
}

void bda_local_ref_init(struct bda_state_info * s)
{
    s->critical = 0;
    s->local_frame_top = NULL;
}

void bda_local_ref_enter(struct bda_state_info * s, int capacity, int sentinel)
{
  struct bda_local_frame * new_frame;
  jobject * ref_list;

  /* Allocate memory. */
  assert(capacity >= -1);
  new_frame = (struct bda_local_frame*)malloc(sizeof(struct bda_local_frame));
  assert(new_frame != NULL);
  if (capacity > 0) {
    ref_list =  (jobject *)calloc(capacity, sizeof(jobject));
    memset(ref_list, 0, sizeof(jobject) * capacity);
    assert(ref_list != NULL);
  } else {
    ref_list = NULL;
  }

  /* Create and add a frame. */
  new_frame->sentinel = sentinel;
  new_frame->capacity = capacity;
  new_frame->count = 0;
  new_frame->list = ref_list;
  new_frame->next = s->local_frame_top;
  s->local_frame_top = new_frame;  
}

void bda_local_ref_leave(struct bda_state_info * s)
{
  struct bda_local_frame * top_frame;
  struct bda_local_frame * next_top_frame;
  jobject * top_ref_list;

  top_frame = s->local_frame_top;
  assert(top_frame != NULL);  
  top_ref_list = top_frame->list;
  next_top_frame = top_frame->next;

  if (top_frame->capacity > 0) {
    assert(top_ref_list != NULL);
    free(top_ref_list);
  } else {
    assert(top_ref_list == NULL);
  }
  free(top_frame);

  s->local_frame_top = next_top_frame;
}

void bda_local_ref_add(struct bda_state_info *s, jobject o)
{
  struct bda_local_frame * top_frame;

  assert(o != NULL);
  top_frame = s->local_frame_top;
  if (top_frame->count < top_frame->capacity) {
    top_frame->list[top_frame->count++] = o;
  }
}

void bda_local_ref_delete(struct bda_state_info *s, jobject o)
{
  struct bda_local_frame * top_frame;
  int i, j;

  assert(o != NULL);  
  top_frame = s->local_frame_top;
  for(i = top_frame->count - 1; i >= 0;i--) {
    if (top_frame->list[i] == o) {
      for(j = i + 1; j < top_frame->count; j++) {
        top_frame->list[j-1] = top_frame->list[j];
      }
      top_frame->count--;
      break;
    }
  }
}

void bda_global_ref_add(jobject o, int weak)
{
  if (weak) {
    hashtable_insert(bda_weak_global_ref_table, (void*)o, (void*)o);
  } else {
    hashtable_insert(bda_global_ref_table, (void*)o, (void*)o);
  }
}

void bda_global_ref_delete(jobject o, int weak)
{
  if (weak) {
    hashtable_remove(bda_weak_global_ref_table, (void*)o);
  } else {
    hashtable_remove(bda_global_ref_table, (void*)o);
  }
}

int bda_check_env_match(struct bda_state_info * s, JNIEnv *env, const char * fname)
{
  struct bda_state state;

  if (env != s->env){
    bda_init_state(&state, env, 0, 
                   "JNI warn (env=%p): Invalid env (=%p) where the correct value is %p in %s\n",
                   s->env, env, s->env);
    bda_cbp(JNI_WARNING, env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_no_exeception(struct bda_state_info * s, const char * fname)
{
  struct bda_state state;

  if (bda_orig_jni_funcs->ExceptionCheck(s->env) == JNI_TRUE){
      bda_init_state(&state, s->env, 0,
                     "JNI warn (env=%p): JNI function call with a pending exception: %s\n", 
                     s->env, fname);
      bda_orig_jni_funcs->ExceptionDescribe(s->env);
      bda_orig_jni_funcs->ExceptionClear(s->env);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
  }
  return 1;
}

int bda_check_non_null(struct bda_state_info * s, const void* o, int index, const char * fname)
{
  struct bda_state state;

  if ((o == NULL) || (o == ((void*)0xFFFFFFFF))) {
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): JInvalid parameter at %d'th to %s\n", 
                     s->env, index, fname);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
  }
  return 1;
}

static int  bda_check_field_common(struct bda_state_info * s, 
                                   struct bda_check_jfieldid_info * finfo, 
                                   jfieldID fid,
                                   jclass oclass, jboolean is_static, 
                                   const char * func_name) 
{
  struct bda_state state;

  if (finfo == NULL) {
    bda_init_state(&state, s->env, 0, "JNI warn (env=%p): An invalid fieldID %p to %s.\n",
                   s->env, fid, func_name);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }

  if (is_static == JNI_TRUE) {
    if ((finfo->modifier & JVM_ACC_STATIC) != JVM_ACC_STATIC) {
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): An instance fieldID %p when a static field is expected in %s.\n",
                     s->env, finfo->fid, func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);      
      return 0;
    }
  } else {
    assert(is_static == JNI_FALSE);
    if ((finfo->modifier & JVM_ACC_STATIC) == JVM_ACC_STATIC) {
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): A static fieldID %p when an instance field is expected in %s.\n",
                     s->env, finfo->fid, func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
    }
  }
  return 1;
}

static int bda_check_field_type_getter(
    struct bda_state_info * s, 
    struct bda_check_jfieldid_info* finfo, 
    jclass oclass,
    char vt, const char * func_name)
{
  const char *fdesc;
  struct bda_state state;
  
  fdesc = finfo->fdesc;
  if ((fdesc[0] != 'L') && (fdesc[0] != '[')) {
    if (fdesc[0] != vt) {
        bda_init_state(&state, s->env, 0, 
                       "JNI warn (env=%p): The type of fieldID %f (=%s) does not match the return type of %s.\n",
                       s->env, finfo->fid, bda_jmethod_primitive_name(fdesc[0]), func_name);
        bda_cbp(JNI_WARNING, s->env, &state, NULL);
        return 0;
    }
  } else {
    if (vt != 'O') {
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): The type of fieldID %f (=%s) does not match the return type of %s.\n",
                     s->env, finfo->fid, fdesc, func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);      
      return 0;
    }
  }
  return 1;
}

static jclass bda_find_class_from_fdesc(JNIEnv *env, const char * fdesc)
{
  const char * cdesc;
  jclass clazz;
  cdesc = bda_str_dup_fdesc_to_cdesc(fdesc);
  clazz = bda_orig_jni_funcs->FindClass(env, cdesc);
  free((void*)cdesc);
  return clazz;
}

static int bda_check_field_type_setter(
    struct bda_state_info * s, 
    struct bda_check_jfieldid_info* finfo, 
    jclass oclass, 
    char vt, const jvalue v, const char * func_name)
{
  const char *fdesc;
  struct bda_state state;

  fdesc = finfo->fdesc;
  if ((fdesc[0] != 'L') && (fdesc[0] != '[')) {
    if (fdesc[0] != vt) {
      bda_init_state(&state, s->env, 0, 
                       "JNI warn (env=%p): The type of fieldID %p does not match the source type of %s in %s.\n",
                       s->env, finfo->fid, bda_jmethod_primitive_name(fdesc[0]), func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
    }
  } else {
    if (vt != 'O') {
      bda_init_state(&state, s->env, 0, 
                       "JNI warn (env=%p): The type of fieldID %p  does not match the source type of %s in %s.\n",
                       s->env, finfo->fid, fdesc, func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
    } else if ( v.l != NULL ) {
      jclass fclass, vclass;
      jboolean assignable;

      fclass = bda_find_class_from_fdesc(s->env, fdesc);
      assert(fclass != NULL);
      vclass = bda_orig_jni_funcs->GetObjectClass(s->env, v.l);
      assert(vclass != NULL);
      assignable = bda_orig_jni_funcs->IsAssignableFrom(s->env, vclass, fclass);
      bda_orig_jni_funcs->DeleteLocalRef(s->env, fclass);
      bda_orig_jni_funcs->DeleteLocalRef(s->env, vclass);
      if (assignable != JNI_TRUE) {
        bda_init_state(&state, s->env, 0,
                       "JNI warn (env=%p): The type of fieldID %p (=%s) is not assignable "
                       "to the target type (%s) in %s.\n",
                       s->env, finfo->fid, fdesc, finfo->fdesc, func_name);
        bda_cbp(JNI_WARNING, s->env, &state, NULL);
        return 0;
      }
    }
  }
  return 1;
}

int bda_check_jfieldid_to_reflected_field(struct bda_state_info * s, jclass c, jfieldID f, jboolean is_static)
{
  struct bda_check_jfieldid_info* finfo;
  int success;

  finfo = bda_jfieldID_lookup(s, c, f, is_static);
  if ((finfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}
  success = bda_check_field_common(s, finfo, f, c, is_static, "ToReflectedField");
  return success;
}

int bda_check_jfieldid_get_instance(struct bda_state_info * s, jobject o, jfieldID f, char vt, const char * func_name)
{
  jclass oclass;
  struct bda_check_jfieldid_info* finfo;
  int success;

  oclass = bda_orig_jni_funcs->GetObjectClass(s->env, o);
  assert(oclass != NULL);
  finfo = bda_jfieldID_lookup(s, oclass, f, JNI_FALSE);
  if ((finfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}
  success = bda_check_field_common(s, finfo, f, oclass, JNI_FALSE, func_name)
      && bda_check_field_type_getter(s, finfo, oclass, vt, func_name);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, oclass);
  return success;
}

int bda_check_jfieldid_set_instance(struct bda_state_info * s, jobject o, jfieldID f, 
                                     const jvalue v, char vt, const char * func_name)
{
  struct bda_check_jfieldid_info* finfo;
  jclass oclass;
  int success;

  oclass = bda_orig_jni_funcs->GetObjectClass(s->env, o);
  assert(oclass != NULL);
  finfo = bda_jfieldID_lookup(s, oclass, f, JNI_FALSE);
  if ((finfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}

  success = bda_check_field_common(s, finfo, f, oclass, JNI_FALSE, func_name)
      && bda_check_field_type_setter(s, finfo, oclass, vt, v, func_name);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, oclass);
  return success;
}

int bda_check_jfieldid_get_static(struct bda_state_info * s, jclass c, jfieldID f,  char vt, const char * func_name)
{
  struct bda_check_jfieldid_info* finfo;
  int success;
  finfo = bda_jfieldID_lookup(s, c, f, JNI_TRUE);
  if ((finfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}
  success = bda_check_field_common(s, finfo,  f,c, JNI_TRUE, func_name)
      && bda_check_field_type_getter(s, finfo, c, vt, func_name);
  return success;
}

int bda_check_jfieldid_set_static(struct bda_state_info * s, jclass c, jfieldID f, 
                                   const jvalue v, char vt, const char * func_name)
{
  struct bda_check_jfieldid_info* finfo;
  int success;
  finfo = bda_jfieldID_lookup(s, c, f, JNI_TRUE);
  success = bda_check_field_common(s, finfo, f, c, JNI_TRUE, func_name) 
      && bda_check_field_type_setter(s, finfo, c, vt, v, func_name);
  return success;
}

static int bda_check_method_static(struct bda_state_info * s, 
                                   const struct bda_check_jmethodID_info * minfo,
                                   jmethodID mid, const char * func_name)
{
  struct bda_state state;

  if (minfo == NULL) {
    bda_init_state(&state, s->env, 0, "JNI warn (env=%p): An invalid jmethodID %p to %s.\n",
                   s->env, mid, func_name);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  assert(mid == minfo->mid);
  if (!minfo->is_static) {
      bda_init_state(&state, s->env, 0,
                     "JNI warn (env=%p): An instance methodID %p when a static method is expected in %s.\n",
                     s->env, mid, func_name);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
  }
  return 1;
}

static int bda_check_method_instance(struct bda_state_info * s, 
                                     const struct bda_check_jmethodID_info * minfo,
                                     jmethodID mid, const char * fname)
{
  struct bda_state state;

  if (minfo == NULL) {
    bda_init_state(&state, s->env, 0, "JNI warn (env=%p): An invalid jmethodID %p to %s.\n",
                   s->env, mid, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  assert(mid == minfo->mid);
  if (minfo->is_static) {
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): A static methodID %p when an instance method is expected in %s.\n",
                   s->env, mid, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

static int bda_check_method_constructor(
    struct bda_state_info * s, const struct bda_check_jmethodID_info * minfo,
    const char * fname)
{
  struct bda_state state;  
  jvmtiError err;
  char *mname, *mdesc;
  int mdesc_len;
  jmethodID m;
  int mname_ok, mdesc_ok;

  m = minfo->mid;
  err = (*bda_jvmti)->GetMethodName(bda_jvmti, m, &mname, &mdesc, NULL);
  assert(err == JVMTI_ERROR_NONE);
  mname_ok = strcmp(mname, "<init>") != 0;
  mdesc_len = strlen(mdesc);
  mdesc_ok = minfo->return_type == 'V';
  (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)mname);
  (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)mdesc);

  if (mname_ok) {
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): the jmethodID %p must be constructor, but its name is %s in %s.\n",
                   s->env, minfo->mid, mname, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }

  if (mdesc_ok) {
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): the jmethodID %p must be constructor, but its return type is not void.\n",
                   s->env, minfo->mid);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

static int bda_check_method_hierachy_equal(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jclass clazz, const char * fname)
{
  struct bda_state state;
  jvmtiError err;
  jclass mclazz;
  jmethodID m = minfo->mid;

  err = (*bda_jvmti)->GetMethodDeclaringClass(bda_jvmti, m, &mclazz);
  assert (err == JVMTI_ERROR_NONE);
  jboolean same = bda_orig_jni_funcs->IsSameObject(s->env, clazz, mclazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, mclazz);
  if (same == JNI_FALSE) {
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): The declaring class of methodID %p is not equal to the target class %p in %s.\n",
                   s->env, m, clazz, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}


static int bda_check_method_hierachy_superclass(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jclass clazz, const char * fname)
{
  struct bda_state state;
  jvmtiError err;
  jclass mclazz;
  jmethodID m = minfo->mid;
  jboolean superclass;

  err = (*bda_jvmti)->GetMethodDeclaringClass(bda_jvmti, m, &mclazz);
  assert (err == JVMTI_ERROR_NONE);
  superclass = bda_is_super_class(s->env, mclazz, clazz);  
  if (!superclass) {
      bda_init_state(&state, s->env, 0,
                     "JNI warn (env=%p): The declaring class of methodID %p is not super class of the target class in %s.\n",
                     s->env, m, fname);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);      
      return 0;
  }
  return 1;
}

static int bda_check_method_hierachy_assignable(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jclass clazz, const char * fname)
{
  struct bda_state state;
  jvmtiError err;
  jclass mclazz;
  jmethodID m = minfo->mid;

  err = (*bda_jvmti)->GetMethodDeclaringClass(bda_jvmti, m, &mclazz);
  assert (err == JVMTI_ERROR_NONE);
  jboolean assignable = bda_orig_jni_funcs->IsAssignableFrom(s->env, clazz, mclazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, mclazz);
  if (!assignable) {
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): The declaring class of methodID %p is not assignable from to the target class in %s.\n",
                   s->env, m, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

static int bda_check_method_hierachy_superclass_obj(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jobject obj, const char * fname)
{
    int success;
    jclass obj_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
    success = bda_check_method_hierachy_superclass(s, minfo, obj_clazz, fname);
    bda_orig_jni_funcs->DeleteLocalRef(s->env, obj_clazz);
    return success;
}

static int bda_check_method_hierachy_equals_obj(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jobject obj, const char * fname)
{
    int success;
    jclass obj_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
    success = bda_check_method_hierachy_equal(s, minfo, obj_clazz, fname);
    bda_orig_jni_funcs->DeleteLocalRef(s->env, obj_clazz);
    return success;
}

static int bda_check_method_hierachy_assignable_obj(
    struct bda_state_info * s,  const struct bda_check_jmethodID_info * minfo,
    jobject obj, const char * fname)
{
    int success;
    jclass obj_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
    success = bda_check_method_hierachy_assignable(s, minfo, obj_clazz, fname);
    bda_orig_jni_funcs->DeleteLocalRef(s->env, obj_clazz);
    return success;
}

static int bda_check_method_arguments(
    struct bda_state_info *s,  const struct bda_check_jmethodID_info * minfo,
    struct bda_var_arg_wrap awrap, const char * fname)
{
  int index;

  for(index = 0; index < minfo->num_arguments;index++) {
    const char * cdesc = minfo->argument_types[index];
    switch(cdesc[0]) {
    case 'B': case 'C': case 'I':  case 'S':case 'Z': case 'F':
      if (awrap.type == BDA_VA_LIST) {
          va_arg(awrap.value.ap, jint);
      }
      break;
    case 'D': case 'J' :
      if (awrap.type == BDA_VA_LIST) {
        va_arg(awrap.value.ap, jlong);
      }
      break;
    case 'L': case '[': {
      jobject object;
      if (awrap.type == BDA_VA_LIST) {
          object = va_arg(awrap.value.ap, jobject);
      } else {
          object = awrap.value.array[index].l;
      }
      if (object != NULL) {
       if (!bda_check_dref(s, object, minfo->num_arguments + index + 1, fname)) {
           return 0;
       }
       if (! bda_check_assignable_jobject_jclass(s, object, cdesc, minfo->num_arguments + index + 1, fname)){
           return 0;
       }
      }
    }
        break;
    default:
      assert(0);
      break;
    }
  }
  return 1;
}

static int bda_check_method_return_type(
    struct bda_state_info *s,  const struct bda_check_jmethodID_info * minfo, 
    char rt, const char * fname)
{
  return 1;
}

static int bda_check_method_is_private_or_constructor(
    struct bda_state_info *s,  const struct bda_check_jmethodID_info * minfo)
{
  jint fmodifier;
  jvmtiError err;
  int result = 0;
  jmethodID m = minfo->mid;

  err = (*bda_jvmti)->GetMethodModifiers(bda_jvmti, m, &fmodifier);
  assert(err == JVMTI_ERROR_NONE);
  if ((fmodifier & JVM_ACC_PRIVATE) == JVM_ACC_PRIVATE) {
      result = 1;
  } else {
      char *mname, *mdesc;
      int mdesc_len;

      err = (*bda_jvmti)->GetMethodName(bda_jvmti, m, &mname, &mdesc, NULL);
      assert(err == JVMTI_ERROR_NONE);
      if (strcmp(mname, "<init>")  == 0) {
          mdesc_len = strlen(mdesc);        
          if ((mdesc_len >= 3) && (mname[mdesc_len - 2] == ')') && (mname[mdesc_len -1] = 'V' )) {
              result = 1;
          }
      }
      (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)mname);
      (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)mdesc);
  }

  return result;
}


static jboolean bda_is_array(JNIEnv *env, jobject obj) {
  jboolean is_array;
  jclass ref_class;
  jvmtiError err;

  ref_class = bda_orig_jni_funcs->GetObjectClass(env, obj);
  err =(*bda_jvmti)->IsArrayClass(bda_jvmti, ref_class, &is_array);
  assert(err == JVMTI_ERROR_NONE);
  bda_orig_jni_funcs->DeleteLocalRef(env, ref_class);

  return is_array;
}

int bda_check_jmethodid_to_reflected(struct bda_state_info * s, jclass c, jmethodID m, jboolean b, const char *fname)
{
  return 0;
}

int bda_check_jmethodid_new_object(struct bda_state_info * s, jclass clazz, 
                                    jmethodID m, struct bda_var_arg_wrap args, 
                                    const char *fname)
{
  const struct bda_check_jmethodID_info * minfo;

  minfo = bda_check_jmethodID_info_lookup(m);
  if ((minfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}

  return  bda_check_method_instance(s, minfo, m, fname)
      && bda_check_method_constructor(s, minfo, fname)
      && bda_check_method_hierachy_equal(s, minfo, clazz, fname)
      && bda_check_method_arguments(s, minfo, args, fname);
}

int bda_check_jmethodid_instance(struct bda_state_info * s, jobject obj, 
                                  jmethodID mid, struct bda_var_arg_wrap args, 
                                  const char *fname, char rt)
{
  const struct bda_check_jmethodID_info * minfo;
  
  minfo = bda_check_jmethodID_info_lookup(mid);
  if ((minfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}

  if (bda_check_method_is_private_or_constructor(s, minfo)) {
      return bda_check_method_instance(s, minfo, mid, fname)
          && bda_check_method_hierachy_equals_obj(s, minfo, obj, fname)
          && bda_check_method_return_type(s, minfo, rt, fname)
          && bda_check_method_arguments(s, minfo, args, fname);
  } else {
      return bda_check_method_instance(s, minfo, mid, fname)
          && bda_check_method_hierachy_assignable_obj(s, minfo, obj, fname)
          && bda_check_method_return_type(s, minfo, rt, fname)
          && bda_check_method_arguments(s, minfo, args, fname);
  }
}

int bda_check_jmethodid_nonvirtual(struct bda_state_info * s, jobject obj, 
                                    jclass clazz, jmethodID mid, 
                                    struct bda_var_arg_wrap args, 
                                    const char *fname, char rt)
{
  const struct bda_check_jmethodID_info * minfo;

  minfo = bda_check_jmethodID_info_lookup(mid);
  if ((minfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}

  return bda_check_method_instance(s, minfo, mid, fname)
      && bda_check_method_hierachy_superclass(s, minfo, clazz, fname)
      && bda_check_method_hierachy_superclass_obj(s, minfo, obj, fname)
      && bda_check_method_arguments(s, minfo, args, fname)
      && bda_check_method_return_type(s, minfo, rt, fname);
}

int bda_check_jmethodid_static(struct bda_state_info * s, jclass clazz, 
                                jmethodID methodID, struct bda_var_arg_wrap args, 
                                const char *fname, char rt)
{
  const struct bda_check_jmethodID_info * minfo;

  minfo = bda_check_jmethodID_info_lookup(methodID);
  if ((minfo == NULL) && (s->mode == SYS_NATIVE)) {return 1;}

  return bda_check_method_static(s, minfo, methodID, fname)
      && bda_check_method_hierachy_equal(s, minfo, clazz, fname)
      && bda_check_method_return_type(s, minfo, rt, fname)
      && bda_check_method_arguments(s, minfo, args, fname);
}

int bda_check_dref(struct bda_state_info * s,  jobject obj, int index, const char * fname) {
  if ((obj != NULL) &&!bda_check_live(s, obj)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): A dead JNI reference at %d'th to %s\n",
                     s->env, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}


int bda_check_jclass(struct bda_state_info * s, jclass clazz, int index, const char * fname)
{
    return (clazz != NULL) && 
     bda_check_instance_jobject_jclass(s, clazz, bda_clazz_class, index, fname);
}

int bda_check_jstring(struct bda_state_info * s, jstring jstr, int index, const char * fname)
{
    return (jstr != NULL) && bda_check_instance_jobject_jclass(s, jstr, bda_clazz_string, index, fname);
}

int bda_check_jthrowable(struct bda_state_info * s, jthrowable t, int index, const char * fname)
{
  return (t != NULL) && bda_check_subclass_jobject_jclass(s, t, bda_clazz_throwable, index, fname);
}

int bda_check_jweak(struct bda_state_info *s, jweak ref, int index, const char * fname)
{  
  return (ref != NULL) &&
      bda_check_jobject_ref_type(s, ref, JNIWeakGlobalRefType, index, fname);
}

int bda_check_jarray(struct bda_state_info *s, jarray ref, int index, const char * fname)
{
  if (ref == NULL) {return 1;}

  if (!bda_is_array(s->env, ref)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): Not a array object at %d'th to %s\n",
                   s->env, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_jbooleanArray(struct bda_state_info *s, jbooleanArray ref, int index, const char * fname)
{
  return bda_check_jarray(s, ref, index, fname)  &&
  bda_check_instance_jobject_jclass(s, ref, bda_clazz_booleanArray, index, fname);
}

int bda_check_jbyteArray(struct bda_state_info *s, jbyteArray ref, int index, const char * fname)
{
    return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
        bda_check_instance_jobject_jclass(s, ref, bda_clazz_byteArray,index, fname);
}

int bda_check_jcharArray(struct bda_state_info *s, jcharArray ref, int index, const char * fname)
{
  return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_charArray,index, fname);  
}

int bda_check_jshortArray(struct bda_state_info *s, jshortArray ref, int index, const char * fname)
{
  return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_shortArray,index, fname);  
}

int bda_check_jintArray(struct bda_state_info *s, jintArray ref, int index, const char * fname)
{
  return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_intArray,index, fname);  
}

int bda_check_jlongArray(struct bda_state_info *s, jlongArray ref, int index, const char * fname)
{
  return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_longArray,index, fname);  
}

int bda_check_jfloatArray(struct bda_state_info *s, jfloatArray ref, int index, const char * fname)
{
    return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_floatArray,index, fname);  
}

int bda_check_jdoubleArray(struct bda_state_info *s, jdoubleArray ref, int index, const char * fname)
{
    return (ref != NULL) && bda_check_jarray(s, ref, index, fname) &&
      bda_check_instance_jobject_jclass(s, ref, bda_clazz_doubleArray,index, fname);  
}

int bda_check_jobjectArray(struct bda_state_info *s, jobjectArray ref, int index, const char * fname)
{
  jvmtiError err;
  jclass ref_class;
  char * cdesc;
  int is_object_array;

  if (ref == NULL) {return 1;}
  if (!bda_check_jarray(s, ref, index, fname)) {return 0;}

  ref_class = bda_orig_jni_funcs->GetObjectClass(s->env, ref);
  err = (*bda_jvmti)->GetClassSignature(bda_jvmti, ref_class, &cdesc, NULL);
  assert(err == JVMTI_ERROR_NONE);
  is_object_array = (cdesc[0] == '[') && ((cdesc[1] == 'L') || (cdesc[1] == '['));
  bda_orig_jni_funcs->DeleteLocalRef(s->env, ref_class);
  err = (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char *)cdesc);
  assert(err == JVMTI_ERROR_NONE);

  if (!is_object_array) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): Not an object array reference at %d'th to %s\n",
                   s->env, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_instance_jobject_jclass(struct bda_state_info *s, jobject obj, jclass o_class, int index, const char * fname)
{
  jclass ref_class;
  jboolean is_same;

  if (obj == NULL) {return 1;}

  ref_class = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
  is_same = bda_orig_jni_funcs->IsSameObject(s->env, ref_class, o_class);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, ref_class);

  if (is_same == JNI_FALSE) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): %p is not right type at %d'th to %s\n",
                   s->env, obj, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}


int bda_check_subclass_jclass_jclass(struct bda_state_info *s, jclass clazz, jclass sup_clazz, int index, const char * fname)
{
  jboolean is_super_class;

  if (clazz == NULL) {return 1;}
  is_super_class = bda_is_super_class(s->env, sup_clazz, clazz);

  if (!is_super_class) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): %p is not an ancestor of the class %p at %d'th to %s\n",
                   s->env, sup_clazz, clazz, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}


int bda_check_subclass_jobject_jclass(struct bda_state_info *s, jobject obj, jclass sup_clazz, int index, const char * fname)
{
  jclass sub_clazz;
  jboolean is_super_class;

  if (obj == NULL) {return 1;}
  sub_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
  is_super_class = bda_is_super_class(s->env, sup_clazz, sub_clazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, sub_clazz);

  if (!is_super_class) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p): %p is not an ancestor of the class of %p at %d'th to %s\n",
                   s->env, sup_clazz, obj, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}


int bda_check_assignable_jclass_jobject(struct bda_state_info *s, jclass clazz, jobject obj, int index, const char * fname)
{
  jclass source_class;
  jboolean is_assignable;

  if (obj == NULL) {return 1;}
  source_class = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
  is_assignable = bda_orig_jni_funcs->IsAssignableFrom(s->env, source_class, clazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, source_class);

  if (!is_assignable) {
      struct bda_state state;
      bda_init_state(&state, s->env, 0, 
                     "JNI warn (env=%p): %p is not assignable to %p at %d'th to %s\n",
                     s->env, obj, clazz, index, fname);
      bda_cbp(JNI_WARNING, s->env, &state, NULL);
      return 0;
  }
  return 1;
}

const char * bda_get_ref_type_name(jobjectRefType ref_type)
{
  switch(ref_type){
  case JNIInvalidRefType: return "invalid";
  case JNILocalRefType: return "local";
  case JNIGlobalRefType: return "global";
  case JNIWeakGlobalRefType:return "weak global";
  default: assert(0); return "";
  }
}

int bda_check_jobject_ref_type(struct bda_state_info *s, jobject obj, jobjectRefType expected_ref_type, int index, const char * fname)
{
  int success = 0;

  switch(expected_ref_type) {
  case JNIInvalidRefType:
      assert(0);
      break;
  case JNILocalRefType: {
    struct bda_local_frame * frame;
    int i;
    for (frame = s->local_frame_top; frame != NULL; frame = frame->next){
        for(i = frame->count -1; i >= 0;i--) {
            if (frame->list[i] == obj) {
                success = 1;
            }
        }
    }
    break;
  }
  case JNIGlobalRefType:
      success = hashtable_search(bda_global_ref_table, (void*)obj) == obj;
      break;
  case JNIWeakGlobalRefType:
      success = hashtable_search(bda_weak_global_ref_table, (void*)obj) == obj;
      break;
  }

  if (!success && s->mode == USR_NATIVE) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): Not a %s reference in the %d's paramenter to %s\n",
                   s->env, bda_get_ref_type_name(expected_ref_type), index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_assignable_jobjectArray_jobject(struct bda_state_info *s, jobjectArray array, jobject obj, int index, const char * fname)
{
  char * cdesc;
  jclass array_clazz, element_clazz, obj_clazz;
  jboolean assignable;
  jvmtiError err;

  array_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, array);
  err = (*bda_jvmti)->GetClassSignature(bda_jvmti, array_clazz, &cdesc, NULL);
  assert(err == JVMTI_ERROR_NONE);
  assert(cdesc[0] == '[');
  bda_orig_jni_funcs->DeleteLocalRef(s->env, array_clazz);
  element_clazz = bda_orig_jni_funcs->FindClass(s->env, &cdesc[1]);
  err = (*bda_jvmti)->Deallocate(bda_jvmti, (unsigned char*)cdesc);
  assert(err == JVMTI_ERROR_NONE);

  obj_clazz = bda_orig_jni_funcs->GetObjectClass(s->env, obj);
  assignable = bda_orig_jni_funcs->IsAssignableFrom(s->env, obj_clazz, element_clazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, element_clazz);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, obj_clazz);

  if (!assignable) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): The object %p is not assignable to the array %p at %d'th to %s. \n",
                   obj, array, s->env, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_jclass_scalar_allocatable(struct bda_state_info *s, jclass clazz, int index, const char * fname)
{
  jint modifier;
  jboolean is_array;
  jvmtiError err;

  err = (*bda_jvmti)->GetClassModifiers(bda_jvmti, clazz, &modifier);
  assert(err == JVMTI_ERROR_NONE);
  err =(*bda_jvmti)->IsArrayClass(bda_jvmti, clazz, &is_array);
  assert(err == JVMTI_ERROR_NONE);

  if (is_array || (modifier&JVM_ACC_INTERFACE) || (modifier & JVM_ACC_ABSTRACT)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): %p is not scalar class at %d'th to %s.\n",
                   s->env, clazz, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_jarray_primitive(struct bda_state_info *s, jarray array, int index, const char * fname)
{
  char *cdesc;
  int is_primitive_array;
  jclass clazz;
  jvmtiError err;

  clazz = bda_orig_jni_funcs->GetObjectClass(s->env, array);
  err = (*bda_jvmti)->GetClassSignature(bda_jvmti, clazz, &cdesc, NULL);
  assert(err == JVMTI_ERROR_NONE);
  assert(cdesc[0] == '[');
  bda_orig_jni_funcs->DeleteLocalRef(s->env, clazz);

  is_primitive_array = 0;  
  if (cdesc[2] == '\0') {
      switch(cdesc[1]) {
      case 'Z':case 'B':case 'C':case 'S':case 'I':case 'J':case 'F':case 'D':
          is_primitive_array = 1;
          break;
      }
  }

  if (!is_primitive_array) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): The %p is not primitive array at %d'th to %s. \n",
                   s->env, array, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_jobject_reflected_method(struct bda_state_info *s, jobject obj, int index, const char * fname)
{
  return bda_check_instance_jobject_jclass(s, obj, bda_clazz_method,index, fname)
      && bda_check_instance_jobject_jclass(s, obj, bda_clazz_constructor,index, fname);
}

int bda_check_capacity_j2c_return(struct bda_state_info * s)
{
  struct bda_local_frame * top_frame;

  top_frame = s->local_frame_top;
  if ((top_frame == NULL) || (top_frame->sentinel)
      || (top_frame->next == NULL)
      || (!top_frame->next->sentinel)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p) :  local frame stack is inconsistant.\n",
                   s->env);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }

  return 1;
}

int bda_check_capacity_c2j_call(struct bda_state_info * s, const char *fname)
{
  struct bda_local_frame * top_frame;

  top_frame = s->local_frame_top;
  if (top_frame->count >= top_frame->capacity) {
    struct bda_state state;
    int i;
    jobject * new_list;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p) : The local references may exceed the current capacity %d in %s\n",
                   s->env, top_frame->capacity, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);

    /* double the capacity. */
    new_list =  (jobject *)calloc(top_frame->capacity * 2, sizeof(jobject));
    assert(new_list != NULL);
    for(i=0; i < top_frame->count;i++) {
      new_list[i] = top_frame->list[i];
    }
    free(top_frame->list);
    top_frame->capacity = top_frame->capacity * 2;
    top_frame->list = new_list;
    return 0;
  }

  return 1;
}

int bda_check_access_set_instance_field(struct bda_state_info * s, jobject o, jfieldID fid, int index, const char *fname)
{
  struct bda_check_jfieldid_info * i;
  jclass clazz;

  clazz = bda_orig_jni_funcs->GetObjectClass(s->env, o);
  i = bda_jfieldID_lookup(s, clazz, fid, JNI_FALSE);
  bda_orig_jni_funcs->DeleteLocalRef(s->env, clazz);

  if ((i != NULL) && ((i->modifier & JVM_ACC_FINAL) == JVM_ACC_FINAL) && !(i->mutable_final)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p) : The field %p is final at %d'th to %s.\n",
                   s->env, fid, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }

  return 1;
}

int bda_check_access_set_static_field(struct bda_state_info * s, jclass c, jfieldID fid, int index, const char *fname)
{
  struct bda_check_jfieldid_info * i;

  i = bda_jfieldID_lookup(s, c, fid, JNI_TRUE);
  if ((i != NULL) && ((i->modifier & JVM_ACC_FINAL) == JVM_ACC_FINAL) && !(i->mutable_final)) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p) : The field %p is final at %d'th to %s.\n",
                   s->env, fid, index, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
    return 0;
  }

  return 1;

}

void bda_resource_acquire(struct bda_state_info * s, const void * resource, const char * fname)
{
  hashtable_insert(bda_resource_table, (void*)resource, (void*)fname);
}

void bda_resource_release(struct bda_state_info * s, const void * resource, const char * fname)
{
  hashtable_remove(bda_resource_table, (void*)resource);
}

int bda_check_resource_free(struct bda_state_info * s, const void * resource, const char *fname)
{
  const char *found = hashtable_search(bda_resource_table, (void*)resource);
  if (!found) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0, 
                   "JNI warn (env=%p): double free of VM resource %p\n",
                   s->env, resource);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
  }
  return 1;
}

int bda_check_resource_leak(JNIEnv *env)
{
  if (hashtable_count(bda_resource_table) > 0 ) {
    struct bda_state state;
    struct hashtable_itr * i;
    
    printf("The following VM resoures are not released.\n");
    printf("%10s   %20s\n", "resource", "allocator");
    i= hashtable_iterator(bda_resource_table);
    do {
      const void * resource = hashtable_iterator_key(i);
      const char * fname = hashtable_iterator_value(i);
      printf("%10p   %20s\n", resource, fname);
    } while(hashtable_iterator_advance(i));
    free(i);

    bda_init_state(&state, env, 0, 
                   "JNI warn (env=%p): VM resource leak\n",
                   env);
    bda_cbp(JNI_WARNING, env, &state, NULL);
    return 0;
  }
  return 1;
}

int bda_check_no_critical(struct bda_state_info * s, const char *fname)
{
  if (s->critical != 0 ) {
    struct bda_state state;
    bda_init_state(&state, s->env, 0,
                   "JNI warn (env=%p) : a function call %s in the JNI critical region\n",
                   s->env, fname);
    bda_cbp(JNI_WARNING, s->env, &state, NULL);
  }
  return 1;
}

void bda_enter_critical(struct bda_state_info * s)
{
  s->critical++;
}

void bda_leave_critical(struct bda_state_info * s)
{
  s->critical--;
}
