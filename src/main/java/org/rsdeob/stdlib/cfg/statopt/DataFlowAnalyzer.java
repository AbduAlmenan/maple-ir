package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.cfg.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;

import java.util.HashMap;
import java.util.HashSet;

import static org.rsdeob.stdlib.cfg.statopt.DataFlowState.CopySet;

public class DataFlowAnalyzer {
	private final ControlFlowGraph cfg;
	private boolean checkRhs;
	private final HashSet<CopyVarStatement> allCopies;

	public DataFlowAnalyzer(ControlFlowGraph cfg, boolean checkRhs) {
		this.cfg = cfg;
		this.checkRhs = checkRhs;

		allCopies = new HashSet<>();
		for (BasicBlock b : cfg.blocks()) {
			for (Statement stmt : b.getStatements()) {
				if (stmt instanceof CopyVarStatement) {
					allCopies.add((CopyVarStatement) stmt);
				}
			}
		}
	}

	// compute forward data flow (available expressions)
	public HashMap<BasicBlock, DataFlowState> computeForward() {
		HashMap<BasicBlock, DataFlowState> dataFlow = new HashMap<>();

		// Compute first block
		dataFlow.put(cfg.getEntry(), computeFirstBlock());

		// Compute initial out for each block
		for (BasicBlock b : cfg.blocks()) {
			if (b == cfg.getEntry())
				continue;
			DataFlowState state = compute(b);
			for (CopyVarStatement copy : allCopies) {
				if (copy.getBlock() == b)
					continue;
				if (state.kill.contains(copy))
					continue;
				state.out.put(copy.getVariable(), copy);
			}
			dataFlow.put(b, state);
		}

		for (boolean changed = true; changed;) {
			changed = false;

			for (BasicBlock b : cfg.blocks()) {
				if (b == cfg.getEntry())
					continue;

				DataFlowState state = dataFlow.get(b);
				CopySet oldOut = new CopySet(state.out);

				// IN[b] = MEET(OUT[p] for p in predicates(b))
				CopySet in = new CopySet();
				for (FlowEdge e : b.getPredecessors())
					in = in.isEmpty() ? dataFlow.get(e.src).out : in.meet(dataFlow.get(e.src).out);
				state.in = in;

				// OUT[b] = GEN[b] UNION (IN[b] - KILL[b])
				for (CopyVarStatement copy : state.in.values())
					if (!state.kill.contains(copy))
						state.out.put(copy.getVariable(), copy);
				for (CopyVarStatement copy : state.gen)
					state.out.put(copy.getVariable(), copy);

				dataFlow.put(b, state);
				if (!state.out.equals(oldOut))
					changed = true;
			}
		}

		return dataFlow;
	}

	private DataFlowState computeFirstBlock() {
		DataFlowState state = compute(cfg.getEntry());
		for (CopyVarStatement copy : state.gen)
			state.out.put(copy.getVariable(), copy);
		return state;
	}

	private DataFlowState compute(BasicBlock b) {
		return new DataFlowState(computeGen(b), computeKill(b));
	}

	// GEN[b] = copies in b that reach end of block (no lhs or rhs redefinition)
	private HashSet<CopyVarStatement> computeGen(BasicBlock b) {
		CopySet gen = new CopySet();
		for (Statement stmt : b.getStatements()) {
			if (!(stmt instanceof CopyVarStatement))
				continue;
			CopyVarStatement newCopy = (CopyVarStatement) stmt;
			gen.put(newCopy.getVariable(), newCopy);
		}

		return new HashSet<>(gen.values());
	}

	// KILL[b] = all copies anywhere in the cfg that do not have lhs/rhs redefined in b
	private HashSet<CopyVarStatement> computeKill(BasicBlock b) {
		HashSet<CopyVarStatement> kill = new HashSet<>();
		for (CopyVarStatement copy : allCopies) {
			if (copy.getBlock() == b)
				continue;

			for (Statement stmt : b.getStatements()) {
				if (!(stmt instanceof CopyVarStatement))
					continue;
				CopyVarStatement newCopy = (CopyVarStatement) stmt;

				// Add all existing statements that would be overwritten by this
				if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
					kill.add(copy);
					break;
				}
				if (checkRhs && copy.isAffectedBy(newCopy)) {
					kill.add(copy);
					break;
				}
			}
		}

		return kill;
	}
}
