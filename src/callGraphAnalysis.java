package com.test;

import java.util.*;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.jimple.spark.ondemand.AllocAndContextSet;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.sets.DoublePointsToSet;
import soot.jimple.spark.sets.EmptyPointsToSet;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.Type;
import soot.Unit;
import soot.UnitPatchingChain;
import soot.Value;
import soot.ValueBox;
import soot.dava.internal.AST.ASTTryNode.container;
import soot.Body;
import soot.BodyTransformer;
import soot.Context;
import soot.EntryPoints;
import soot.Local;
import soot.LocalGenerator;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.RetStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JNewMultiArrayExpr;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MutableDirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.javaToJimple.*;

public class callGraphAnalysis extends SceneTransformer{
	static boolean debug = true;
	
	class methodLocal {
		SootMethod m;
		JimpleLocal l;
		
		public methodLocal(SootMethod m_, JimpleLocal l_) {
			m = m_;
			l = l_;
		}
		
		@Override
		public String toString() {
			return "\nMethod: " + m.toString() + " Local: " + l.toString();
		}
	}
	
	class blockNode{
		Unit unit;
		
		SootMethod containingMethod;
		
		HashSet<Integer> in, out;
		
		Vector<blockNode> succ;

		// create constructor
		blockNode(Unit u, SootMethod sm) {
			unit = u;
			containingMethod = sm;
			
			in = null;
			out = null;
			succ = new Vector<blockNode>();
		}
	}
	
	public class PrintableHashMap<K, V> extends HashMap<K, V>{
		@Override
		public String toString() {
			String s = "";
			
			for(Entry<K, V> entry : entrySet()) {
				s += entry.getKey() + " :\n";
				if(entry.getValue() instanceof Vector) {
					for(Object o : (Vector) entry.getValue()) {
						s += "    " + o + "\n";
					}
				}
				else if(entry.getValue() instanceof HashSet) {
					for(Object o : (HashSet) entry.getValue()) {
						s += "    " + o + "\n";
					}
				}
				else {
					s += "    " + entry.getValue() + "\n";
				}
			}
			
			return s;
		}
	}
	
	PrintableHashMap<SootClass, SootClass> parent = new PrintableHashMap<SootClass, SootClass>();
	PrintableHashMap<SootClass, Vector<SootClass>> child = new PrintableHashMap<SootClass, Vector<SootClass>>();
	PrintableHashMap<SootMethod, Vector<SootMethod>> overloadedMethods = new PrintableHashMap<SootMethod, Vector<SootMethod>>();
	
	PrintableHashMap<Integer, Vector<methodLocal>> object2local = new PrintableHashMap<Integer, Vector<methodLocal>>();
	PrintableHashMap<methodLocal, Vector<Integer>> local2object = new PrintableHashMap<methodLocal, Vector<Integer>>();
	PrintableHashMap<Integer, PrintableHashMap<SootField, Vector<Integer>>> object2object = new PrintableHashMap<Integer, PrintableHashMap<SootField, Vector<Integer>>>();
	PrintableHashMap<Integer, PrintableHashMap<SootField, Vector<Integer>>> object2object2 = new PrintableHashMap<Integer, PrintableHashMap<SootField, Vector<Integer>>>();
	PrintableHashMap<SootMethod, HashSet<Integer>> methodCreates = new PrintableHashMap<SootMethod, HashSet<Integer>>();
	PrintableHashMap<SootMethod, HashSet<Integer>> methodUses = new PrintableHashMap<SootMethod, HashSet<Integer>>();
	
	HashMap<SootMethod, Vector<blockNode>> methodGraph = new HashMap<SootMethod, Vector<blockNode>>();
	HashMap<SootMethod, Vector<blockNode>> methodHeads = new HashMap<SootMethod, Vector<blockNode>>();
	HashMap<SootMethod, blockNode> methodReturns = new HashMap<SootMethod, blockNode>();
	PrintableHashMap<SootMethod, HashSet<Integer>> methodKilled = new PrintableHashMap<SootMethod, HashSet<Integer>>();


	HashMap<SootMethod, Boolean> freeingVisited = new HashMap<SootMethod, Boolean>();
	// methodKilled gives the set of objects killed in each method

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		CallGraph cg = Scene.v().getCallGraph();
		PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
		
		// get the parent class relations
		for(SootClass sc : Scene.v().getApplicationClasses()) {
			if(sc.getName().startsWith("jdk", 0)) continue;
			
			if(sc.hasSuperclass()) {
				SootClass superClass = null;
				if(!sc.getSuperclass().getName().equals("java.lang.Object"))
					superClass = sc.getSuperclass();
				
				parent.put(sc, superClass);
			}
		}
		
		for(SootClass sc : parent.keySet()) {
			SootClass parentClass = parent.get(sc);
			
			if(parentClass != null) {
				if(!child.containsKey(parentClass)) child.put(parentClass, new Vector<SootClass>());
				
				child.get(parentClass).add(sc);
			}
		}
		
		for(SootClass sc : child.keySet()) {
			for(SootMethod sm : sc.getMethods()) {
				String name = sm.getName();
				List<Type> paramTypes = sm.getParameterTypes();
				Type retType = sm.getReturnType();
				
				Queue<SootClass> childs = new LinkedList<SootClass>();
				for(SootClass childClass : child.get(sc)) childs.add(childClass);
				
				while(!childs.isEmpty()) {
					SootClass childClass = childs.poll();
					
					if(childClass.declaresMethod(name, paramTypes, retType)) {
						SootMethod childMethod = childClass.getMethod(name, paramTypes, retType);
						
						if(!overloadedMethods.containsKey(sm)) overloadedMethods.put(sm, new Vector<SootMethod>());
						overloadedMethods.get(sm).add(childMethod);
					}
					
					if(child.containsKey(childClass)) {
						for(SootClass next : child.get(childClass)) childs.add(next);
					}
				}
			}
		}
		
		SootMethod mainMethod = Scene.v().getMainMethod();
		
		iterateCallGraph(cg, pta, mainMethod, new HashMap<SootMethod, Boolean>());
		
		
		for(Integer o1 : object2object.keySet()) {
			for(SootField f : object2object.get(o1).keySet()) {
				for(Integer o2 : object2object.get(o1).get(f)) {
					if(!object2object2.containsKey(o2)) object2object2.put(o2, new PrintableHashMap<SootField, Vector<Integer>>());
					
					if(!object2object2.get(o2).containsKey(f)) object2object2.get(o2).put(f, new Vector<Integer>());
					
					object2object2.get(o2).get(f).add(o1);
				}
			}
		}
		
		
		if(debug) {
			System.out.println("Child classes:");
			System.out.println(child);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Overloaded Methods");
			System.out.println(overloadedMethods);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Local to Object Map:");
			System.out.println(local2object);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Object to Local Map:");
			System.out.println(object2local);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Object to Object Map:");
			System.out.println(object2object2);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Objects created in every method");
			System.out.println(methodCreates);
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println("Objects used in every method");
			System.out.println(methodUses);
			System.out.println("------------------------------------------------------------------------------------");
		}

		createMethodGraph();
		
		if(debug) {
			for(SootMethod sm: methodGraph.keySet()) {
				// for each blockNode in the methodGraph, print successors
				System.out.println("------------------------------------------------------------------------------------");
				System.out.println("Method: " + sm);
				for(blockNode bn: methodGraph.get(sm)) {
					System.out.println("Unit: " + bn.unit + bn.unit.getTags().toString());
					System.out.println("Successors: ");
					for(blockNode succ: bn.succ) {
						System.out.println("    " + succ.unit + succ.unit.getTags().toString());
					}
				}
				System.out.println();
			}
			System.out.println("------------------------------------------------------------------------------------");
		}

		simplifyMethodGraphs();
		
		if(debug) {
			for(SootMethod sm: methodGraph.keySet()) {
				// for each blockNode in the methodGraph, print successors
				System.out.println("------------------------------------------------------------------------------------");
				System.out.println("Method: " + sm);
				for(blockNode bn: methodGraph.get(sm)) {
					System.out.println("Unit: " + bn.unit + bn.unit.getTags().toString());
					System.out.println("Successors: ");
					for(blockNode succ: bn.succ) {
						System.out.println("    " + succ.unit + succ.unit.getTags().toString());
					}
				}
				System.out.println();
			}
			System.out.println("------------------------------------------------------------------------------------");
		}
		
		performContextInsensitiveAnalysis();
		
		if(debug) {
			for(SootMethod sm: methodGraph.keySet()) {
				// for each blockNode in the methodGraph, print successors
				System.out.println("------------------------------------------------------------------------------------");
				System.out.println("Method: " + sm);
				for(blockNode bn: methodGraph.get(sm)) {
					System.out.println("Unit: " + bn.unit + bn.unit.getTags().toString());
					System.out.println("Out");
					System.out.println(bn.out);
					System.out.println("In");
					System.out.println(bn.in);
				}
				System.out.println();
			}
			System.out.println("------------------------------------------------------------------------------------");
		}
		
		if(debug) {
			System.out.println("Objects killed in each method:");
			System.out.println(methodKilled);
		}

		for(SootMethod sm: methodGraph.keySet()){
			System.out.println("Before");
			System.out.println(sm.getActiveBody().toString());
			
			freeLinks(methodKilled.get(sm), sm);
			
			System.out.println("After");
			System.out.println(sm.getActiveBody().toString());
		}
		
	}
	
	
	void iterateCFG(SootMethod rootMethod, PointsToAnalysis pta) {
		Boolean debug = false;
		if(debug) {
			System.out.println("------------------------------------------------------------------------------------");
			System.out.println(rootMethod);
		}
		
		for(Local l : rootMethod.getActiveBody().getLocals()) {
			
			if(debug) {
				System.out.println("\n" + l);
				System.out.println(l.getType());
				System.out.println(l.getType().getClass().getClass());
			}
			
			PointsToSet pts = pta.reachingObjects(l);
			
			if(pts instanceof EmptyPointsToSet) continue;
			
			methodLocal ml = new methodLocal(rootMethod, (JimpleLocal) l);
			
			Vector<Integer> v = new Vector<Integer>();
			((DoublePointsToSet) pts).forall( new P2SetVisitor() {
				
				@Override
				public void visit(Node n) {
					if(!object2local.containsKey(n.getNumber())) {
						object2local.put(n.getNumber(), new Vector<methodLocal>());
					}
					object2local.get(n.getNumber()).add(ml);
					
					v.add(n.getNumber());
				}
			});
			
			local2object.put(ml, v);
		}
		
		ExceptionalUnitGraph g = new ExceptionalUnitGraph(rootMethod.retrieveActiveBody());
		
		HashMap<Unit, Boolean> visited_unit = new HashMap<Unit, Boolean>();
		
		HashSet<Integer> creates = new HashSet<Integer>();
		HashSet<Integer> uses = new HashSet<Integer>();
		
		Queue<Unit> q = new LinkedList<Unit>();
		
		Vector<blockNode> blockHeads = new Vector<blockNode>();
		
		for(Unit head : g.getHeads()) {
			q.add(head);
			
			blockNode bn = new blockNode(head, rootMethod);
			blockHeads.add(bn);
		}
		
		while(!q.isEmpty()) {
			Unit cur = q.poll();
			
			if(visited_unit.containsKey(cur))
				continue;
		
			visited_unit.put(cur, true);
			
			Stmt s = (Stmt) cur;
			
			for(Unit next : g.getSuccsOf(cur))
				q.add(next);
			
			if(debug) {
				System.out.println();
				System.out.println(s);
				System.out.println(s.getClass());
				System.out.println("Use Boxes");
				for(ValueBox vb : s.getUseBoxes()) {
					System.out.println(vb.getValue() + " - " + vb.getValue().getClass());
				}
				System.out.println("Def Boxes");
				for(ValueBox vb : s.getDefBoxes()) {
					System.out.println(vb.getValue() + " - " + vb.getValue().getClass());
				}
			}
			
			Boolean hasInvocation = false;
			List<Value> args = null;
			for(ValueBox vb : s.getUseBoxes()) {
				if(vb.getValue() instanceof InvokeExpr) {
					hasInvocation = true;
					args = ((InvokeExpr) vb.getValue()).getArgs();
					break;
				}
			}
			
			if(debug && hasInvocation) {
				for(Value v : args) {
					System.out.println("Arg : " + v);
				}
			}
			
			for(ValueBox vb : s.getUseBoxes()) {
				if(vb.getValue() instanceof ParameterRef) {
					JimpleLocal l = (JimpleLocal) s.getDefBoxes().get(0).getValue();
					PointsToSet pts = pta.reachingObjects(l);
					
					if(!(pts instanceof EmptyPointsToSet)) {
					
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								uses.add(n.getNumber());
								if(debug)
									System.out.println("Added " + n.getNumber() + " to use set");
							}
						});
					}
				}
				else if(vb.getValue() instanceof ThisRef) {
					JimpleLocal l = (JimpleLocal) s.getDefBoxes().get(0).getValue();
					
					if(l.getName().startsWith("temp")) continue;
					
					PointsToSet pts = pta.reachingObjects(l);
					
					if(!(pts instanceof EmptyPointsToSet)) {
					
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								uses.add(n.getNumber());
								if(debug)
									System.out.println("Added " + n.getNumber() + " to use set");
							}
						});
					}
				}
				else if(vb.getValue() instanceof JimpleLocal) {
					JimpleLocal l = (JimpleLocal) vb.getValue();
					
					if(l.getName().startsWith("temp$")) continue;
					
					PointsToSet pts = pta.reachingObjects(l);
					
					if(!(pts instanceof EmptyPointsToSet)) {
					
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								uses.add(n.getNumber());
								if(debug)
									System.out.println("Added " + n.getNumber() + " to use set");
							}
						});
					}
				}
				else if(vb.getValue() instanceof JInstanceFieldRef) {					
					JimpleLocal l = (JimpleLocal) ((JInstanceFieldRef) vb.getValue()).getBase();
					SootField f = (SootField) ((JInstanceFieldRef) vb.getValue()).getField();
					
					PointsToSet pts = pta.reachingObjects(l);
					Vector<Integer> pointsTo_l = new Vector<Integer>();
					if(!(pts instanceof EmptyPointsToSet)) {
						
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								pointsTo_l.add(n.getNumber());
								uses.add(n.getNumber());
							}
						});
					}
					
					pts = pta.reachingObjects(l, f);
					
					for(Integer o : pointsTo_l) {
						if(!object2object.containsKey(o))
							object2object.put(o, new PrintableHashMap<SootField, Vector<Integer>>());
						
						if(!object2object.get(o).containsKey(f))
							object2object.get(o).put(f, new Vector<Integer>());
					}
					
					if(!(pts instanceof EmptyPointsToSet)) {
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								for(Integer o : pointsTo_l) {
									object2object.get(o).get(f).add(n.getNumber());
									uses.add(n.getNumber());
								}
							}
						});
					}
				}
				else if(vb.getValue() instanceof JNewExpr || vb.getValue() instanceof JNewArrayExpr || vb.getValue() instanceof JNewMultiArrayExpr) {
					JimpleLocal l = (JimpleLocal) (((DefinitionStmt) s).getLeftOp());
					
					PointsToSet pts = pta.reachingObjects(l);
					
					if(!(pts instanceof EmptyPointsToSet)) {
					
						((DoublePointsToSet) pts).forall(new P2SetVisitor() {
							
							@Override
							public void visit(Node n) {
								creates.add(n.getNumber());
							}
						});
					}
				}
			}
		}
		
		methodCreates.put(rootMethod, creates);
		methodUses.put(rootMethod, uses);
		
		methodHeads.put(rootMethod, blockHeads);
		methodGraph.put(rootMethod, new Vector<blockNode>()); 
	}
	
	
	void iterateCallGraph(CallGraph cg, PointsToAnalysis pta, SootMethod rootMethod, HashMap<SootMethod, Boolean> visited) {		
		if(visited.containsKey(rootMethod))
			return;
		
		visited.put(rootMethod, true);
		
		if(rootMethod.getDeclaringClass().getName().startsWith("java", 0) || rootMethod.isConstructor()) return;	
		
		// iterate the CFG and get all points to information
		iterateCFG(rootMethod, pta);

		// iterate over the units in the graph
		Iterator<Edge> edges = cg.edgesOutOf(rootMethod);
		while(edges.hasNext()) {
			iterateCallGraph(cg, pta, (SootMethod) edges.next().getTgt(), visited);
		}
	}

	
	void createMethodGraph() {
		for(SootMethod sm: methodHeads.keySet()) {
			List<blockNode> heads = methodHeads.get(sm);
			
			
			HashMap<Unit, blockNode> u2b = new HashMap<Unit, blockNode>();

			ExceptionalUnitGraph g = new ExceptionalUnitGraph(sm.retrieveActiveBody());
			
			Queue<blockNode> q = new LinkedList<blockNode>();
			HashMap<Unit, Boolean> visited = new HashMap<Unit, Boolean>();

			for (blockNode bn: heads) {
				q.add(bn);
				visited.put(bn.unit, false);
				u2b.put(bn.unit, bn);
			}

			while(!q.isEmpty()) {
				blockNode bn = q.poll();
				visited.put(bn.unit, true);
				
				methodGraph.get(sm).add(bn);

				List<Unit> succs = g.getSuccsOf(bn.unit);
				for (Unit succ: succs) {
					// add the successor to the successor list of the current node
					blockNode succNode;
					
					if(u2b.containsKey(succ)) succNode = u2b.get(succ);
					else {
						succNode = new blockNode(succ, sm);
						u2b.put(succ, succNode);
					}
					
					bn.succ.add(succNode);

					// if the successor is already visited, continue
					if(visited.containsKey(succ)) continue;
					
					q.add(succNode);
					visited.put(succ, false);
				}
			}
		}
	}

	
	void simplifyMethodGraphs() {
		for(SootMethod sm: methodGraph.keySet()) {
			simplifyCFG(methodGraph.get(sm), methodHeads.get(sm));
			
			removeEmptyBlocks(methodGraph.get(sm));
		}
		
		for(SootMethod sm: methodGraph.keySet()) {
			addReturnEdges(methodGraph.get(sm));
		}
	}
	
	
	void simplifyCFG(Vector<blockNode> cfg, Vector<blockNode> heads) {
		HashMap<Unit, Boolean> visited = new HashMap<Unit, Boolean>();
		
		Queue<blockNode> q = new LinkedList<blockNode>();
		for(blockNode head: heads) {
			q.add(head);
			visited.put(head.unit, false);
		}

		while(!q.isEmpty()) {
			blockNode u = q.poll();
			visited.put(u.unit, true);

			Vector<blockNode> succsLevel1 = u.succ;

			if(succsLevel1.size() == 0) continue;
			
			int i = 0;
			while(i < succsLevel1.size()) {
				blockNode succsLevel1_i = succsLevel1.get(i);

				// if the node is a invoke statement, continue
				Boolean hasInvocation = false;
				for(ValueBox vb : ((Stmt) succsLevel1_i.unit).getUseBoxes()) {
					if(vb.getValue() instanceof InvokeExpr && !((InvokeExpr) vb.getValue()).getMethod().isConstructor()) {
						hasInvocation = true;
						break;
					}
				}
				
				if(hasInvocation) {
					q.add(succsLevel1_i);
					visited.put(succsLevel1_i.unit, false);
					i++;
					continue;
				}

				Vector<blockNode> succsLevel2 = succsLevel1_i.succ;
				
				// if the successor, succsLevel1_i, of the current node, u, has only one successor, remove this intermediate successor and
				// add the second level successor to the successor list of the current node, u.
				if(succsLevel2.size()==1) {
					for(blockNode u2 : cfg) {
						if(u2.succ.contains(succsLevel1_i) && u2 != u) {
							u2.succ.remove(succsLevel1_i);
							u2.succ.addAll(succsLevel2);
						}
					}

					succsLevel1.remove(succsLevel1_i);
					succsLevel1.addAll(succsLevel2);
					
					visited.put(succsLevel2.get(0).unit, false);
					q.add(succsLevel2.get(0));

					succsLevel1_i.succ.clear();
					visited.put(succsLevel1_i.unit, true);
				}
				else {
					i++;
					
					if(visited.containsKey(succsLevel1_i.unit) && visited.get(succsLevel1_i.unit)) continue;
					
					q.add(succsLevel1_i);
					visited.put(succsLevel1_i.unit, false);
				}
			}
		}
	}
	
	
	void removeEmptyBlocks(Vector<blockNode> cfg){
		Vector<blockNode> toRemove = new Vector<blockNode>();
		for(blockNode u : cfg) {
			if(!(((Stmt) u.unit) instanceof RetStmt) && !(((Stmt) u.unit) instanceof ReturnStmt) && !(((Stmt) u.unit) instanceof ReturnVoidStmt)) {
				if(u.succ.size() == 0)
					toRemove.add(u);
			}
			else {
				methodReturns.put(u.containingMethod, u);
			}
		}

		for(blockNode u: toRemove)
			cfg.remove(u);
	}
	
	
	void addReturnEdges(Vector<blockNode> cfg) {
		for(blockNode bn : cfg) {
			Boolean hasInvocation = false;
			SootMethod calledMethod = null;
			for(ValueBox vb : ((Stmt) bn.unit).getUseBoxes()) {
				if(vb.getValue() instanceof InvokeExpr && !((InvokeExpr) vb.getValue()).getMethod().isConstructor() && !((InvokeExpr) vb.getValue()).getMethod().isJavaLibraryMethod()) {
					hasInvocation = true;
					calledMethod = ((InvokeExpr) vb.getValue()).getMethod();
					break;
				}
			}
			
			if(hasInvocation) {
				Vector<blockNode> returnNodes = new Vector<blockNode>();
				
				returnNodes.add(methodReturns.get(calledMethod));
				
				if(overloadedMethods.containsKey(calledMethod)) {
					for(SootMethod sm : overloadedMethods.get(calledMethod))
						returnNodes.add(methodReturns.get(sm));		
				}
				
				for(blockNode rn : returnNodes) {
					rn.succ.add(bn);
				}
			}
		}
	}
	
	
	void performContextInsensitiveAnalysis() {
		Queue<blockNode> worklist = new LinkedList<blockNode>();;
		
		for(SootMethod sm : methodGraph.keySet()) {
			for(blockNode gn : methodGraph.get(sm))
				worklist.add(gn);
		}

		Queue<blockNode> next = new LinkedList<blockNode>();

		Boolean changed = true;

		while(changed) {
			changed = false;

			while(!worklist.isEmpty()) {
				blockNode gn = worklist.poll();

				// in[n] = U out[p] for all p in pred[n]
				Stmt s = (Stmt) gn.unit;
				
				Boolean hasInvocation = false;
				SootMethod invokedMethod = null;
				for(ValueBox vb : s.getUseBoxes()) {
					if(vb.getValue() instanceof InvokeExpr && !((InvokeExpr) vb.getValue()).getMethod().isJavaLibraryMethod()) {
						hasInvocation = true;
						invokedMethod = ((InvokeExpr) vb.getValue()).getMethod();
						break;
					}
				}
				
				HashSet<Integer> in_ = new HashSet<Integer>();
				HashSet<Integer> out_ = new HashSet<Integer>();

				if(s instanceof IdentityStmt) {
					// out[n] = in[succ], in[n] = out[n] - create[method]

					for(blockNode succ : gn.succ) {
						if(succ.in != null)
							out_.addAll(succ.in);
					}

					in_.addAll(out_);
					in_.removeAll(methodCreates.get(gn.containingMethod));
				}
				else if(s instanceof ReturnStmt || s instanceof RetStmt || s instanceof ReturnVoidStmt) {
					// out[n] = U out[succ], in[n] = out[n] U uses[method]

					for(blockNode succ : gn.succ) {
						if(succ.out != null)
							out_.addAll(succ.out);
					}

					in_.addAll(out_);
					in_.addAll(methodUses.get(gn.containingMethod));
				}
				else if(hasInvocation) {
					// out[n] = in[succ], in[n] = in[entry node of its cfg]

					for(blockNode succ : gn.succ) {
						if(succ.in != null)
							out_.addAll(succ.in);
					}

					Vector<SootMethod> possibleRuntimeMethods = new Vector<SootMethod>();
					possibleRuntimeMethods.add(invokedMethod);
					
					if(overloadedMethods.containsKey(invokedMethod)) 
						possibleRuntimeMethods.addAll(overloadedMethods.get(invokedMethod));
					
					for(SootMethod sm : possibleRuntimeMethods) {
						for(blockNode entry : methodHeads.get(sm)) {
							if(entry.in != null)
								in_.addAll(entry.in);
						}
					}
				}
				else {
					// branch node, out[n] = U in[succ], in[n] = out[n]

					for(blockNode succ : gn.succ) {
						if(succ.in != null)
							out_.addAll(succ.in);
					}

					in_.addAll(out_);
				}

				if((gn.in == null) || (gn.out == null) || (gn.in != null && !in_.equals(gn.in)) || (gn.out != null && !out_.equals(gn.out))) {
					changed = true;
					gn.in = in_;
					gn.out = out_;
				}

				next.add(gn);
			}

			worklist = next;
			next = new LinkedList<blockNode>();
		}

		// set the killed set for each node
		for(SootMethod sm : methodGraph.keySet()) {			
			blockNode entryNode = methodHeads.get(sm).get(0);
			
			HashSet<Integer> killed = new HashSet<Integer>();

			killed.addAll(entryNode.in);
			
			for(blockNode gn : methodGraph.get(sm)) {
				if(gn.unit instanceof ReturnStmt || gn.unit instanceof RetStmt || gn.unit instanceof ReturnVoidStmt) {
					killed.removeAll(gn.out);
				}
			}

			methodKilled.put(sm, killed);
		}
	}
	
	void freeLinks(HashSet<Integer> killSet, SootMethod callerMethod){
		if(debug)
			System.out.println("-----------Caller Method: " + callerMethod + "start------------------");
		for(Integer obj: killSet){
			if(debug)
				System.out.println("Freeing object " + obj + " in method " + callerMethod);
			// get object,field pairs that are pointing to this object
			if(object2object2.containsKey(obj)){
				// list of all <f,O'> such that O'.f = O
				PrintableHashMap<SootField, Vector<Integer>> fieldObjPair = object2object2.get(obj);
				
				for(SootField f: fieldObjPair.keySet()){
					Vector<Integer> objects = fieldObjPair.get(f);
					for(Integer o: objects){
						// o.f = obj
						if(debug) System.out.println("Going to enter findPointer to free Object: " + o + " field: " + f);
						Local local = findPointer(o, f, callerMethod);
						if(debug)
							System.out.println(local + "." + f + " = null in method " + callerMethod);
						
						// TODO: fix findPointer -> if null, needs to look at parent methods too, remove below after fixing it.
						if(local==null) {
							continue;
						}
						
						SootFieldRef fieldRef = f.makeRef();
						InstanceFieldRef fieldRefExpr = Jimple.v().newInstanceFieldRef(local, fieldRef);
						
						NullConstant nullConst = NullConstant.v();
			            AssignStmt assignStmt = Jimple.v().newAssignStmt(fieldRefExpr, nullConst);
			            
			            if(debug)
			            	System.out.println(assignStmt);
			            
						Body body = callerMethod.getActiveBody();
						UnitPatchingChain units = body.getUnits();
						Unit insertionPoint = methodReturns.get(callerMethod).unit; // Adjust this to the appropriate point in your method
						units.insertBefore(assignStmt, insertionPoint);
					}
				}
			}

		}
	}

	Local findPointer(Integer obj, SootField f, SootMethod callerMethod){
		Vector<methodLocal> locals = object2local.get(obj);
		List<methodLocal> yetToCheckParent = new ArrayList<>();
		for(methodLocal ml: locals){
			if(ml.m == callerMethod){
				// check if the local is pointing to any other object
				int localReturnable = 1;
				if(local2object.containsKey(ml)){
					Vector<Integer> objects = local2object.get(ml);
					for(Integer o: objects){ 
						if(o!=obj){
							yetToCheckParent.add(ml);
							break;
						}
					}
				}
				if(localReturnable==1){
					return ml.l;
				}
			}
		}
		
		//TODO: fix not exactly solve infinite loop
		
		for(methodLocal ml: yetToCheckParent) {
			if(ml.m == callerMethod){
				// check if the local is pointing to any other object
				int localReturnable = 1;
				if(local2object.containsKey(ml)){
					Vector<Integer> objects = local2object.get(ml);
					for(Integer o: objects){ 
						if(o!=obj){
							localReturnable = 0;
							PrintableHashMap<SootField, Vector<Integer>> fieldObjPair = object2object2.get(obj);

							if(fieldObjPair != null) {
								for(SootField f1: fieldObjPair.keySet()){
									Vector<Integer> objects1 = fieldObjPair.get(f1);
									for(Integer o1: objects1){
										Local temp = findPointer(o1, f1, callerMethod);
										if(temp!=null){
											// TO MODIFY
											// newTemp = getNewTemp();
											LocalGenerator lg = new DefaultLocalGenerator(callerMethod.getActiveBody());
											Local newTempLocal = lg.generateLocal(RefType.v("java.lang.Object"));
	
											SootFieldRef fieldRef = f1.makeRef();
											InstanceFieldRef fieldRefExpr = Jimple.v().newInstanceFieldRef(temp, fieldRef);
											AssignStmt assignStmt = Jimple.v().newAssignStmt(newTempLocal, fieldRefExpr);
	
											if(debug)
												System.out.println("newTemp = " + temp + "." + f1);
											
											Body body = callerMethod.getActiveBody();
											UnitPatchingChain units = body.getUnits();
											Unit insertionPoint = methodReturns.get(callerMethod).unit;
											units.insertBefore(assignStmt, insertionPoint);
	
											return newTempLocal;
										}
									}
								}
							}
						}
					}
				}
				if(localReturnable==1){
					return ml.l;
				}
			}
		}
		
		
		return null;
	}

}