package org.mapleir.deob.passes;

import org.mapleir.context.IContext;
import org.mapleir.deob.IPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class CallgraphPruningPass implements IPass {

	@Override
	public String getId() {
		return "CG-Prune";
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		Set<MethodNode> active = cxt.getIRCache().getActiveMethods();
		
		int i = 0;
		
		for(ClassNode cn : cxt.getApplication().iterate()) {
			ListIterator<MethodNode> lit = cn.methods.listIterator();
			while(lit.hasNext()) {
				MethodNode m = lit.next();
				if(!active.contains(m)) {
					lit.remove();
					i++;
				}
			}
		}
		
		System.out.println("Removed " + i + " dead methods.");
		
		return i;
	}
	
	@Override
	public boolean isQuantisedPass() {
		return false;
	}
}