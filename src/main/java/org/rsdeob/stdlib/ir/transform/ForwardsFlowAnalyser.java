package org.rsdeob.stdlib.ir.transform;

import java.util.Iterator;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public abstract class ForwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S>{

	public ForwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	@Override
	protected void init() {
		super.init();
		
		for(N entry : graph.getEntries()) {
			in.put(entry, newEntryState());
			queue.add(entry);
		}
	}

	private void queue(N n) {
		Set<E> edgeSet = graph.getReverseEdges(n);
		if (edgeSet != null) {
			for (E e : edgeSet) {
				N src = e.src;
				if(!queue.contains(src)) {
					queue.add(src);
				}
			}
		}
	}
	
	@Override
	public void remove(N n) {
		super.remove(n);
		queue(n);
	}

	@Override
	public void update(N n) {
		super.update(n);
		replaced(n, n);
	}

	@Override
	public void replaced(N old, N n) {
		super.replaced(old, n);
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
		queue(n);
	}

	@Override
	public void insert(N p, N s, N n) {
		update(n);
		queue(p);
		queue(s);
	}

	@Override
	public void processImpl() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);
			
			if(!graph.containsVertex(n)) {
				continue;
			}
			// System.out.println("fexe " + n);
			
			S oldOut = newState();
			S currentOut = out.get(n);
			copy(currentOut, oldOut);
			
			S currentIn = in.get(n);
			Set<E> preds = graph.getReverseEdges(n);
			
			if(preds.size() == 1) {
				N pred = preds.iterator().next().src;
				S predOut = out.get(pred);
				copy(predOut, currentIn);
			} else if(preds.size() > 1) {
				Iterator<E> it = preds.iterator();
				
				N firstPred = it.next().src;
				copy(out.get(firstPred), currentIn);
				
				while(it.hasNext()) {
					S merging = out.get(it.next().src);
					merge(currentIn, merging);
				}
			}
			
			// System.out.println("in: " + currentIn);
			execute(n, currentIn, currentOut);
			// System.out.println("out: " + currentOut);
			
			// if there was a change, enqueue the successors.
			if (!equals(currentOut, oldOut)) {
				for (E e : graph.getEdges(n)) {
					queue.add(e.dst);
				}
			}
		}
	}
	
	@Override
	protected abstract S newState();

	@Override
	protected abstract S newEntryState();

	@Override
	protected abstract void merge(S in1, S in2, S out);
	
	@Override
	protected abstract void copy(S src, S dst);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void execute(N n, S in, S out);
}