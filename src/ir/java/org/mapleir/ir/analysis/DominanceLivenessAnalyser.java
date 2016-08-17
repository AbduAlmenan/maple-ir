package org.mapleir.ir.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;

public class DominanceLivenessAnalyser {

	private final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> rv;
	private final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> tq;
	private final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> sdoms;
	
	private final ControlFlowGraph cfg;
	private final SSADefUseMap defuse;
	private final BasicBlock entry;
	private final ControlFlowGraph red_cfg;
	private final ExtendedDfs cfg_dfs;
	private final Set<FlowEdge<BasicBlock>> back;
	private final ExtendedDfs reduced_dfs;
	private final TarjanDominanceComputor<BasicBlock> domc;
	
	public DominanceLivenessAnalyser(ControlFlowGraph cfg, SSADefUseMap defuse) {
		this.cfg = cfg;
		this.defuse = defuse;
		
		rv = new NullPermeableHashMap<>(new SetCreator<>());
		tq = new NullPermeableHashMap<>(new SetCreator<>());
		sdoms = new NullPermeableHashMap<>(new SetCreator<>());
		
		if(cfg.getEntries().size() != 1) {
			throw new IllegalStateException(cfg.getEntries().toString());
		}
		
		entry = cfg.getEntries().iterator().next();
		
		cfg_dfs = new ExtendedDfs(cfg, entry, ExtendedDfs.EDGES | ExtendedDfs.PRE /* for sdoms*/ );
		back = cfg_dfs.getEdges(ExtendedDfs.BACK);
		
		red_cfg = reduce(cfg, back);
		reduced_dfs = new ExtendedDfs(red_cfg, entry, ExtendedDfs.POST | ExtendedDfs.PRE);
		
		computeReducedReachability();
		computeTargetReachability();
		
		domc = new TarjanDominanceComputor<>(cfg);
		
		computeStrictDominators();
	}
	
	private void computeStrictDominators() {
		NullPermeableHashMap<BasicBlock, Set<BasicBlock>> sdoms = new NullPermeableHashMap<>(new SetCreator<>());
		// i think this is how you do it..
		for(BasicBlock b : cfg_dfs.getPreOrder()) {
			BasicBlock idom = domc.idom(b);
			if(idom != null) {
				sdoms.getNonNull(b).add(idom);
				sdoms.getNonNull(b).addAll(sdoms.getNonNull(idom));
			}
		}
		
		for(Entry<BasicBlock, Set<BasicBlock>> e : sdoms.entrySet()) {
			for(BasicBlock b : e.getValue()) {
				this.sdoms.getNonNull(b).add(e.getKey());
			}
		}
	}

	private void computeReducedReachability() {
		for (BasicBlock b : reduced_dfs.getPostOrder()) {
			rv.getNonNull(b).add(b);
			for (FlowEdge<BasicBlock> e : red_cfg.getReverseEdges(b)) {
				rv.getNonNull(e.src).addAll(rv.get(b));
			}
		}
	}
	
	private void computeTargetReachability() {
		Map<BasicBlock, Set<BasicBlock>> tups = new HashMap<>();
		
		for (BasicBlock b : cfg.vertices()) {
			tups.put(b, tup(b));
		}
		
		for (BasicBlock b : reduced_dfs.getPreOrder()) {
			tq.getNonNull(b).add(b);
			for (BasicBlock w : tups.get(b)) {
				tq.get(b).addAll(tq.get(w));
			}
		}
	}
	
	// Tup(t) = set of unreachable backedge targets from reachable sources
	private Set<BasicBlock> tup(BasicBlock t) {
		Set<BasicBlock> rt = rv.get(t);
		
		// t' in {V - r(t)}
		Set<BasicBlock> set = new HashSet<>(cfg.vertices());
		set.removeAll(rt);
		
		// all s' where (s', t') is a backedge and s'
		//  is in rt.
		
		// set of s'
		Set<BasicBlock> res = new HashSet<>();
		
		for(BasicBlock tdash : set) {
			for(FlowEdge<BasicBlock> pred : cfg.getReverseEdges(tdash)) {
				BasicBlock src = pred.src;
				// s' = src, t' = dst
				if(back.contains(pred) && rt.contains(src)) {
					res.add(pred.dst);
				}
			}
		}
		
		return res;
	}

	private ControlFlowGraph reduce(ControlFlowGraph cfg, Set<FlowEdge<BasicBlock>> back) {
		ControlFlowGraph reducedCfg = cfg.copy();
		for (FlowEdge<BasicBlock> e : back) {
			reducedCfg.removeEdge(e.src, e);
		}
		return reducedCfg;
	}
	
	boolean live_in(BasicBlock b, Local l) {
		BasicBlock defBlock = defuse.defs.get(l);
		
		if(defuse.phis.contains(l) && defBlock == b) {
			return true;
		}
		
		Set<BasicBlock> tqa = new HashSet<>(tq.get(b));
		tqa.retainAll(sdoms.get(defBlock));
		
		for(BasicBlock t : tqa) {
			Set<BasicBlock> rt = new HashSet<>(rv.get(t));
			rt.retainAll(defuse.uses.get(l));
			if(!rt.isEmpty()) {
				return true;
			}
		}
		
		return false;
	}
}