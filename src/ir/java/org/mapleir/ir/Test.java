package org.mapleir.ir;

import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.dot.ControlFlowGraphDecorator;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public class Test {

	void loopTest() {
		int x = 1;
		do {
			if (x > 5)
				x--;
			else
				x++;
		} while (!p());
		System.out.println(x);
	}

	void test111() {
		int x = 1;
		int y = 2;
		do {
			int z = x;
			x = y;
			y = z;
		} while (!p());

		System.out.println(x);
		System.out.println(y);
	}

	void test112() {
		int x = 1, y = 2;
		do {
			int w = x;
			x = y; // y = p() ? x : y
			if (q())
				y = w;
		} while (!p());

		System.out.println(x + y);
	}

	void test113() {
		int x = 1;
		int y = 2;

		while (!p()) {
			int z = x;
			x = y;
			y = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test114() {
		Object o = null;

		while (!p()) {
			o = new Object();
		}

		System.out.println(o);
	}

	void test115() {
		Object o = null;

		do {
			o = new Object();
		} while (!p());

		System.out.println(o);
	}

	void test116() {
		Object o1 = new String("x");
		Object o2 = new String("d");

		do {
			Object o3 = o2;
			o2 = o1;
			o1 = o3;
		} while (!p());

		System.out.println(o1);
		System.out.println(o2);
	}

	void test117() { // i dedicate this test case to my friend revan114
		int lmao = v();
		int x = lmao;
		int y = lmao;

		while (!p()) {
			int z = x;
			x = y;
			y = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test118() {
		int x = 1;
		int y = 2;

		if (q())
			y = x;
		if (p())
			x = y;

		System.out.println(x);
		System.out.println(y);
	}

	void test119() {
		try {
			System.out.println("print");
		} catch (RuntimeException e) {

		}
	}

	void test120() {
		try {
			System.out.println("print");
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	void test121() {
		int x;

		try {
			System.gc();
			x = 5;
		} catch (RuntimeException e) {
			x = 10;
		}

		System.out.println(x);
	}
	
	void trap() {
	}

	void test122() {
		int x = 5;
		int y = 10;

		try {
			trap();
			int z = x; // 5
			trap();
			x = y; // 10
			trap();
			y = z; // 5
			trap();
			// x = 10;
			// y = 5;
		} catch (RuntimeException e) {
			trap();
			int z = y; // 10
			trap();
			y = x; // 5
			trap();
			x = z; // 10
			trap();
			// x = 10;
			// y = 5;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test123() {
		int x = 5;
		int y = 10;

		try {
			int z = x;
			x = y;
			y = z;
		} finally {
			int z = y;
			y = x;
			x = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test011() {
		int x = v();
		int y = u();

		if (p())
			System.out.println(x);
		else
			System.out.println(x + 5);
		System.out.println(y);
	}

	boolean p() {
		return true;
	}

	boolean q() {
		return true;
	}

	int u() {
		return 114;
	}

	int v() {
		return 114;
	}

	public static void dump(ControlFlowGraph cfg, MethodNode m) {
		m.visitCode();
		m.instructions.clear();
		m.tryCatchBlocks.clear();

		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		for (BasicBlock b : cfg.vertices()) {
			m.visitLabel(b.getLabel());
			for (Statement stmt : b) {
				stmt.toCode(m, null);
			}
		}

		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			System.out.println("RANGE: " + er);
			String type = null;
			Set<String> typeSet = er.getTypes();
			if (typeSet.size() == 0 || typeSet.size() > 1) {
				// TODO: fix base exception
				type = Throwable.class.getCanonicalName().replace(".", "/");
			} else {
				// size == 1
				type = typeSet.iterator().next();
			}
			List<BasicBlock> range = er.get();
			Label start = range.get(0).getLabel();
			Label end = null;
			BasicBlock endBlock = range.get(range.size() - 1);
			BasicBlock im = endBlock.getImmediate();
			if (im == null) {
				BasicBlock nextBlock = cfg.getBlock(BasicBlock.createBlockName(BasicBlock.numeric(endBlock.getId()) + 1));
				if (nextBlock != null) {
					end = nextBlock.getLabel();
				} else {
					LabelNode label = new LabelNode();
					m.visitLabel(label.getLabel());
					BasicBlock newExit = new BasicBlock(cfg, endBlock.getNumericId() + 1, label);
					cfg.addVertex(newExit);
					cfg.addEdge(endBlock, new DummyEdge<>(endBlock, newExit));
					end = label.getLabel();
				}
			} else {
				end = im.getLabel();
			}
			Label handler = er.getHandler().getLabel();
			m.visitTryCatchBlock(start, end, handler, type);
		}
		m.visitEnd();
	}

	public static void main(String[] args) throws IOException {
		ClassReader cr = new ClassReader(Test.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);

		// InputStream i = new FileInputStream(new File("res/a.class"));
		// ClassReader cr = new ClassReader(i);
		// ClassNode cn = new ClassNode();
		// cr.accept(cn, 0);

		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while (it.hasNext()) {
			MethodNode m = it.next();

			// if(!m.toString().equals("a/a/f/a.<init>()V")) {
			// continue;
			// }

			// if(!m.toString().equals("org/mapleir/ir/Test.test111()V")) {
			// continue;
			// }

			if (!m.toString().startsWith("org/mapleir/ir/Test.test011")) {
				if (!m.toString().startsWith("org/mapleir/ir/Test.test122")) {
					continue;
				}

				System.out.println("Processing " + m + "\n");
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);

				try {
					BoissinotDestructor destructor = new BoissinotDestructor(cfg);
				} catch (RuntimeException e) {
					throw new RuntimeException("\n" + cfg.toString(), e);
				}

				cfg.getLocals().realloc(cfg);

				BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
				DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
				writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP)).setName("destructed").export();

				MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
				dump(cfg, m2);
				cn.methods.add(m2);
				cn.methods.remove(m);
			}

			ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			cn.accept(clazz);
			byte[] saved = clazz.toByteArray();
			FileOutputStream out = new FileOutputStream(new File("out/testclass.class"));
			out.write(saved, 0, saved.length);
			out.close();

		}
	}
}