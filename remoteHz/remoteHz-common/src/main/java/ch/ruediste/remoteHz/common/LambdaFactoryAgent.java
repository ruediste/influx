package ch.ruediste.remoteHz.common;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class LambdaFactoryAgent {
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new InnerClassLambdaMetafactoryTransformer(), true);
        try {
            inst.retransformClasses(Class.forName("java.lang.invoke.InnerClassLambdaMetafactory"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class InnerClassLambdaMetafactoryTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (className.equals("java/lang/invoke/InnerClassLambdaMetafactory")) {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, 0);
                cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                            String[] exceptions) {
                        if ("<init>".equals(name)) {
                            return new MethodVisitor(Opcodes.ASM5,
                                    super.visitMethod(access, name, desc, signature, exceptions)) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    // set the isSerializable-parameter to true
                                    mv.visitInsn(Opcodes.ICONST_1);
                                    mv.visitVarInsn(Opcodes.ISTORE, 7);
                                };
                            };
                        } else
                            return super.visitMethod(access, name, desc, signature, exceptions);
                    }
                }, 0);
                return cw.toByteArray();
            }
            return null;
        }
    }

}
