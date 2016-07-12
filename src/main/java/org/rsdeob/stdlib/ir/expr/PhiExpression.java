package org.rsdeob.stdlib.ir.expr;

import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class PhiExpression extends Expression {

	private final List<VersionedLocal> locals;
	
	public PhiExpression(List<VersionedLocal> locals) {
		this.locals = locals;
	}
	
	public int getParameterCount() {
		return locals.size();
	}
	
	public VersionedLocal getLocal(int j) {
		return locals.get(j);
	}
	
	public void setLocal(int j, VersionedLocal l) {
		locals.set(j, l);
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public Expression copy() {
		return new PhiExpression(locals);
	}

	@Override
	public Type getType() {
		return Type.VOID_TYPE;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("\u0278" + locals);
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		throw new UnsupportedOperationException("Phi is not executable.");
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof PhiExpression) {
			return ((PhiExpression) s).locals.equals(locals);
		}
		return false;
	}
}