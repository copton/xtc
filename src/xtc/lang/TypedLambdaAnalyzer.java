// ===========================================================================
// This file has been generated by
// Typical, version 1.13.1,
// (C) 2004-2007 Robert Grimm and New York University,
// on Tuesday, October 16, 2007 at 10:43:19 AM.
// Edit at your own risk.
// ===========================================================================

package xtc.lang;

import xtc.util.Pair;
import xtc.util.Runtime;
import xtc.util.Function;

import xtc.tree.Node;
import xtc.tree.GNode;

import xtc.typical.Analyzer;
import xtc.typical.Tuple;
import xtc.typical.Name;
import xtc.typical.Scope;
import xtc.typical.ScopeKind;

/** Type checker for TypedLambda. */
public class TypedLambdaAnalyzer extends Analyzer {
  public TypedLambdaAnalyzer(Runtime runt) {
    super(runt);
    analyzer = analyze;
  }

  public void getScopeNodes() {
    processScopeNodes.add("Abstraction");
  }

  final Function.F1<Tuple.T3<Name, String, String>, Node> getNameSpace = new Function.F1<Tuple.T3<Name, String, String>, Node>() {
    public Tuple.T3<Name, String, String> apply(final Node n) {
      return new Match<Tuple.T3<Name, String, String>>() {
        public Tuple.T3<Name, String, String> apply() {
          final Node arg$0 = GNode.cast(n);

          if (TypedLambdaSupport.match$1(arg$0)) {
            final String id = Analyzer.cast(arg$0.getString(0));

            matching_nodes.add(arg$0);
            if ((null != arg$0 && processScopeNodes.contains(arg$0.getName()))) {
              processScope(arg$0, getScope);
            }
            checkEnterScope(arg$0);

            final Object retValue$4 = Analyzer.cast(new Tuple.T3<Name, String, String>(new Name.SimpleName(id), "default", "type"));

            checkExitScope(arg$0);
            matching_nodes.remove(matching_nodes.size() - 1);
            return Analyzer.cast(retValue$4);
          }
          return null;
        }
      }.apply();
    }
  };

  final Function.F1<Scope, Node> getScope = new Function.F1<Scope, Node>() {
    public Scope apply(final Node n) {
      if (TypedLambdaSupport.match$6(n)) {
        final Node id = Analyzer.cast(n.getGeneric(0));
        final Node body = Analyzer.cast(n.getGeneric(2));

        return Analyzer.cast(new Scope(new ScopeKind.Anonymous("lambda"), new Pair<Node>(id).append(new Pair<Node>(body))));
      }
      return null;
    }
  };

  final Function.F1<TypedLambdaTypes.raw_type<?>, Node> analyze = new Function.F1<TypedLambdaTypes.raw_type<?>, Node>() {
    public TypedLambdaTypes.raw_type<?> apply(final Node arg$8) {
      return new Match<TypedLambdaTypes.raw_type<?>>() {
        public TypedLambdaTypes.raw_type<?> apply() {
          final Node arg$9 = GNode.cast(arg$8);

          if (TypedLambdaSupport.match$10(arg$9)) {
            final Node lambda = Analyzer.cast(arg$9.getGeneric(0));
            final Node expr = Analyzer.cast(arg$9.getGeneric(1));

            matching_nodes.add(arg$9);
            if ((null != arg$9 && processScopeNodes.contains(arg$9.getName()))) {
              processScope(arg$9, getScope);
            }
            checkEnterScope(arg$9);

            final Object retValue$27 = Analyzer.cast(new Let<TypedLambdaTypes.raw_type<?>>() {
              final TypedLambdaTypes.raw_type<?> tl;
              final TypedLambdaTypes.raw_type<?> tr;

              {
                tl = Analyzer.cast(analyze.apply(lambda));
                tr = Analyzer.cast(analyze.apply(expr));
              }

              public TypedLambdaTypes.raw_type<?> apply() {
                return Analyzer.cast(new Require<TypedLambdaTypes.raw_type<?>>() {
                  public TypedLambdaTypes.raw_type<?> apply() {
                    final Boolean var$24 = new Match<Boolean>() {
                      public Boolean apply() {
                        final TypedLambdaTypes.raw_type<?> arg$21 = Analyzer.cast(tl);

                        if ((null != arg$21))
                                                    switch (arg$21.tag()) {
                          case FunctionT:
                            if (TypedLambdaSupport.match$15(arg$21)) {
                              return Analyzer.cast(Boolean.TRUE);
                            }
                            break;
                          default:
                            break;
                          };
                        if (true) {
                          return Analyzer.cast(Boolean.FALSE);
                        }
                        return null;
                      }
                    }.apply().equals(true);

                    if ((null != var$24 && !var$24)) {
                      showMessage("error", "application of non-function", null);
                    }
                    if ((null == var$24)) {
                      return null;
                    }
                    if (var$24) {
                      return new Match<TypedLambdaTypes.raw_type<?>>() {
                        public TypedLambdaTypes.raw_type<?> apply() {
                          final Tuple.T2<TypedLambdaTypes.raw_type<?>, TypedLambdaTypes.raw_type<?>> arg$18 = Analyzer.cast(new Tuple.T2<TypedLambdaTypes.raw_type<?>, TypedLambdaTypes.raw_type<?>>(tl, tr));

                          if (TypedLambdaSupport.match$12(arg$18)) {
                            final TypedLambdaTypes.raw_type<?> param = Analyzer.cast(arg$18.get1().getTuple().get1());
                            final TypedLambdaTypes.raw_type<?> res = Analyzer.cast(arg$18.get1().getTuple().get2());

                            if ((null != arg$18.get2() && arg$18.get2().equals(param))) {
                              return Analyzer.cast(res);
                            }
                          }
                          if (true) {
                            return Analyzer.cast(error("argument/parameter type mismatch", null));
                          }
                          return null;
                        }
                      }.apply();
                    }
                    return null;
                  }
                }.apply());
              }
            }.apply());

            checkExitScope(arg$9);
            matching_nodes.remove(matching_nodes.size() - 1);
            if ((null != arg$9)) {
              arg$9.setProperty("__type", retValue$27);
            }
            return Analyzer.cast(retValue$27);
          }
          if (TypedLambdaSupport.match$6(arg$9)) {
            final Node id = Analyzer.cast(arg$9.getGeneric(0));
            final Node type = Analyzer.cast(arg$9.getGeneric(1));
            final Node body = Analyzer.cast(arg$9.getGeneric(2));

            matching_nodes.add(arg$9);
            if ((null != arg$9 && processScopeNodes.contains(arg$9.getName()))) {
              processScope(arg$9, getScope);
            }
            checkEnterScope(arg$9);

            final Object retValue$31 = Analyzer.cast(new Let<TypedLambdaTypes.raw_type<?>>() {
              final TypedLambdaTypes.raw_type<?> param;
              final TypedLambdaTypes.raw_type<?> res;

              {
                param = Analyzer.cast(get_type.apply(type));
                define3.apply(id, param, getNameSpace);
                res = Analyzer.cast(analyze.apply(body));
              }

              public TypedLambdaTypes.raw_type<?> apply() {
                return Analyzer.cast(new TypedLambdaTypes.FunctionT(param, res));
              }
            }.apply());

            checkExitScope(arg$9);
            matching_nodes.remove(matching_nodes.size() - 1);
            if ((null != arg$9)) {
              arg$9.setProperty("__type", retValue$31);
            }
            return Analyzer.cast(retValue$31);
          }
          if (TypedLambdaSupport.match$32(arg$9)) {
            final Node id = Analyzer.cast(arg$9);

            matching_nodes.add(arg$9);
            if ((null != arg$9 && processScopeNodes.contains(arg$9.getName()))) {
              processScope(arg$9, getScope);
            }
            checkEnterScope(arg$9);

            final Object retValue$35 = Analyzer.cast(Analyzer.cast(lookup2.apply(id, getNameSpace)));

            checkExitScope(arg$9);
            matching_nodes.remove(matching_nodes.size() - 1);
            if ((null != arg$9)) {
              arg$9.setProperty("__type", retValue$35);
            }
            return Analyzer.cast(retValue$35);
          }
          if (TypedLambdaSupport.match$36(arg$9)) {
            matching_nodes.add(arg$9);
            if ((null != arg$9 && processScopeNodes.contains(arg$9.getName()))) {
              processScope(arg$9, getScope);
            }
            checkEnterScope(arg$9);

            final Object retValue$39 = Analyzer.cast(new TypedLambdaTypes.IntegerT());

            checkExitScope(arg$9);
            matching_nodes.remove(matching_nodes.size() - 1);
            if ((null != arg$9)) {
              arg$9.setProperty("__type", retValue$39);
            }
            return Analyzer.cast(retValue$39);
          }
          if (TypedLambdaSupport.match$40(arg$9)) {
            matching_nodes.add(arg$9);
            if ((null != arg$9 && processScopeNodes.contains(arg$9.getName()))) {
              processScope(arg$9, getScope);
            }
            checkEnterScope(arg$9);

            final Object retValue$43 = Analyzer.cast(new TypedLambdaTypes.StringT());

            checkExitScope(arg$9);
            matching_nodes.remove(matching_nodes.size() - 1);
            if ((null != arg$9)) {
              arg$9.setProperty("__type", retValue$43);
            }
            return Analyzer.cast(retValue$43);
          }
          return null;
        }
      }.apply();
    }
  };

  final Function.F1<TypedLambdaTypes.raw_type<?>, Node> get_type = new Function.F1<TypedLambdaTypes.raw_type<?>, Node>() {
    public TypedLambdaTypes.raw_type<?> apply(final Node arg$45) {
      return new Match<TypedLambdaTypes.raw_type<?>>() {
        public TypedLambdaTypes.raw_type<?> apply() {
          final Node arg$46 = GNode.cast(arg$45);

          if (TypedLambdaSupport.match$47(arg$46)) {
            final Node parameter = Analyzer.cast(arg$46.getGeneric(0));
            final Node result = Analyzer.cast(arg$46.getGeneric(1));

            matching_nodes.add(arg$46);
            if ((null != arg$46 && processScopeNodes.contains(arg$46.getName()))) {
              processScope(arg$46, getScope);
            }
            checkEnterScope(arg$46);

            final Object retValue$50 = Analyzer.cast(new TypedLambdaTypes.FunctionT(get_type.apply(parameter), get_type.apply(result)));

            checkExitScope(arg$46);
            matching_nodes.remove(matching_nodes.size() - 1);
            return Analyzer.cast(retValue$50);
          }
          if (TypedLambdaSupport.match$51(arg$46)) {
            matching_nodes.add(arg$46);
            if ((null != arg$46 && processScopeNodes.contains(arg$46.getName()))) {
              processScope(arg$46, getScope);
            }
            checkEnterScope(arg$46);

            final Object retValue$54 = Analyzer.cast(new TypedLambdaTypes.IntegerT());

            checkExitScope(arg$46);
            matching_nodes.remove(matching_nodes.size() - 1);
            return Analyzer.cast(retValue$54);
          }
          if (TypedLambdaSupport.match$55(arg$46)) {
            matching_nodes.add(arg$46);
            if ((null != arg$46 && processScopeNodes.contains(arg$46.getName()))) {
              processScope(arg$46, getScope);
            }
            checkEnterScope(arg$46);

            final Object retValue$58 = Analyzer.cast(new TypedLambdaTypes.StringT());

            checkExitScope(arg$46);
            matching_nodes.remove(matching_nodes.size() - 1);
            return Analyzer.cast(retValue$58);
          }
          return null;
        }
      }.apply();
    }
  };
}
