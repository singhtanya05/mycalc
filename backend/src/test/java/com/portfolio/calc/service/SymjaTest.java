package com.portfolio.calc.service;

import org.junit.jupiter.api.Test;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;
import org.matheclipse.core.interfaces.IAST;
import org.matheclipse.core.form.tex.TeXFormFactory;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SymjaTest {

    @Test
    public void testSymjaAst() {
        ExprEvaluator util = new ExprEvaluator();
        IExpr expr = util.eval("x^3 + 2*x");
        System.out.println("Expression: " + expr);
        System.out.println("Class: " + expr.getClass().getName());
        TeXFormFactory factory = new TeXFormFactory();
        StringBuilder sb = new StringBuilder();
        factory.convert(sb, expr, 0);
        System.out.println("LaTeX: " + sb.toString());

        if (expr.isAST()) {
            IAST ast = (IAST) expr;
            System.out.println("AST Head: " + ast.head());
            for (int i = 1; i < ast.size(); i++) {
                System.out.println("Arg " + i + ": " + ast.get(i) + " | Class: " + ast.get(i).getClass().getName());
            }
        }
        assertNotNull(expr);
    }
}
