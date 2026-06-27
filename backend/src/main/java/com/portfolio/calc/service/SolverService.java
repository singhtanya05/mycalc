package com.portfolio.calc.service;

import com.portfolio.calc.dto.SolveResponse;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.form.tex.TeXFormFactory;
import org.matheclipse.core.interfaces.IAST;
import org.matheclipse.core.interfaces.IExpr;
import org.matheclipse.core.interfaces.ISymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SolverService {

    private static final Logger log = LoggerFactory.getLogger(SolverService.class);
    private final ExprEvaluator util = new ExprEvaluator();
    private final TeXFormFactory texFactory = new TeXFormFactory();

    public SolveResponse solve(String equation, String problemType) {
        log.info("Received solve request. Equation: '{}', Type: '{}'", equation, problemType);
        
        // Clean & preprocess input
        String normalizedInput = preprocessEquation(equation);
        String detectedType = problemType;
        if (detectedType == null || detectedType.trim().isEmpty() || detectedType.equalsIgnoreCase("auto")) {
            detectedType = detectProblemType(normalizedInput);
        }
        
        List<String> steps = new ArrayList<>();
        String finalAnswer = "";
        String latexRepresentation = "";

        try {
            final String fDetectedType = detectedType;
            return CompletableFuture.supplyAsync(() -> {
                switch (fDetectedType.toLowerCase()) {
                    case "derivative":
                        return handleDerivative(normalizedInput, steps);
                    case "integral":
                        return handleIntegral(normalizedInput, steps);
                    case "limit":
                        return handleLimit(normalizedInput, steps);
                    case "algebra":
                    default:
                        return handleAlgebra(normalizedInput, steps);
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Solver timed out for equation: {}", equation);
            throw new IllegalArgumentException("The equation is too complex to be solved within the time limit.");
        } catch (Exception e) {
            log.error("Error solving equation", e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("Could not solve the expression. Please check the syntax: " + cause.getMessage(), cause);
        }
    }

    private String preprocessEquation(String input) {
        if (input == null) return "";
        String cleaned = input.trim();

        // 1. Discard leading OCR bullet points, question numbers, or arrow headers
        String[] markers = {"\\int", "\\frac", "\\sum", "\\lim", "\\cos", "\\sin", "\\tan", "\\log", "\\ln", "d/d", "integrate"};
        int firstIndex = -1;
        for (String marker : markers) {
            int idx = cleaned.indexOf(marker);
            if (idx != -1) {
                if (firstIndex == -1 || idx < firstIndex) {
                    firstIndex = idx;
                }
            }
        }
        if (firstIndex != -1) {
            cleaned = cleaned.substring(firstIndex);
        } else {
            // Strip common bullet formats if no standard math operator prefix is found
            cleaned = cleaned.replaceAll("^(?:\\\\downarrow_\\{[^\\}]*\\}|\\\\downarrow|\\\\uparrow|\\\\rightarrow|\\\\bullet|\\d+\\.|\\s|\\*|_|\\{|\\})+", "");
        }

        // 2. Map LaTeX formatting commands to plain math syntax (with padding space)
        cleaned = cleaned.replace("\\int", " integrate ");
        cleaned = cleaned.replace("\\cos", " cos ");
        cleaned = cleaned.replace("\\sin", " sin ");
        cleaned = cleaned.replace("\\tan", " tan ");
        cleaned = cleaned.replace("\\log", " log ");
        cleaned = cleaned.replace("\\ln", " log ");
        cleaned = cleaned.replace("\\left(", " ( ");
        cleaned = cleaned.replace("\\right)", " ) ");
        cleaned = cleaned.replace("\\{", " ( ");
        cleaned = cleaned.replace("\\}", " ) ");
        cleaned = cleaned.replace("\\cdot", " * ");

        // 3. Remove LaTeX spacing tags
        cleaned = cleaned.replace("\\,", " ");
        cleaned = cleaned.replace("\\;", " ");
        cleaned = cleaned.replace("\\quad", " ");

        // 4. Remove any remaining backslashes
        cleaned = cleaned.replace("\\", "");

        // 5. Connect spaced integration variables (e.g. "d x" -> "dx")
        cleaned = cleaned.replaceAll("(?i)d\\s+([a-z])", "d$1");

        log.info("Preprocessed equation from '{}' to '{}'", input, cleaned);
        return cleaned.trim();
    }

    private String detectProblemType(String input) {
        String lower = input.toLowerCase();
        if (lower.contains("d/d") || lower.contains("derivative") || lower.contains("diff") || lower.startsWith("d(")) {
            return "derivative";
        }
        if (lower.contains("integrate") || lower.contains("int ") || lower.contains("∫") || lower.startsWith("integrate(")) {
            return "integral";
        }
        if (lower.contains("limit") || lower.contains("lim ") || lower.startsWith("limit(")) {
            return "limit";
        }
        return "algebra";
    }

    private SolveResponse handleDerivative(String input, List<String> steps) {
        // Extract expression and variable. Example: d/dx(x^3 + 2x) -> expression = x^3 + 2x, variable = x
        String var = "x";
        String exprStr = input;
        
        // Clean patterns like "d/dx( ... )" or "derivative of ... w.r.t x"
        if (exprStr.toLowerCase().startsWith("d/d")) {
            var = exprStr.substring(3, 4);
            int firstParen = exprStr.indexOf('(');
            int lastParen = exprStr.lastIndexOf(')');
            if (firstParen != -1 && lastParen != -1) {
                exprStr = exprStr.substring(firstParen + 1, lastParen);
            }
        } else if (exprStr.toLowerCase().startsWith("d(")) {
            int firstParen = exprStr.indexOf('(');
            int lastParen = exprStr.lastIndexOf(')');
            if (firstParen != -1 && lastParen != -1) {
                String contents = exprStr.substring(firstParen + 1, lastParen);
                String[] parts = contents.split(",");
                exprStr = parts[0].trim();
                if (parts.length > 1) {
                    var = parts[1].trim();
                }
            }
        }

        IExpr parsedExpr = util.eval(exprStr);
        String exprLatex = toLatex(parsedExpr);
        steps.add("We want to find the derivative of: $$f(" + var + ") = " + exprLatex + "$$ with respect to $" + var + "$.");
        
        // Generate recursive steps
        generateDerivativeSteps(parsedExpr, var, steps);

        IExpr solved = util.eval("D(" + exprStr + ", " + var + ")");
        String solvedLatex = toLatex(solved);
        steps.add("Combining all steps, the final derivative is: $$f'(" + var + ") = " + solvedLatex + "$$");

        return new SolveResponse(steps, solved.toString(), solvedLatex);
    }

    private void generateDerivativeSteps(IExpr expr, String var, List<String> steps) {
        if (!containsVariable(expr, var)) {
            steps.add("Taking the derivative of the constant term $" + toLatex(expr) + "$ with respect to $" + var + "$. Since it doesn't depend on $" + var + "$, its derivative is simply: $0$");
            return;
        }

        if (expr.isSymbol()) {
            if (expr.toString().equals(var)) {
                steps.add("We differentiate the variable $" + var + "$ with respect to itself. Applying the standard rule: $$\\frac{d}{d" + var + "}[" + var + "] = 1$$");
            }
            return;
        }

        if (expr.isAST()) {
            IAST ast = (IAST) expr;
            String headName = ast.head().toString().toLowerCase();

            if (headName.equals("plus")) {
                steps.add("Let's break this down. Since we are differentiating a sum, we can apply the Sum Rule and take the derivative of each term separately: $$\\frac{d}{d" + var + "}[u + v] = \\frac{d}{d" + var + "}[u] + \\frac{d}{d" + var + "}[v]$$");
                for (int i = 1; i < ast.size(); i++) {
                    generateDerivativeSteps(ast.get(i), var, steps);
                }
            } else if (headName.equals("times")) {
                // Check constant multiple
                List<IExpr> varParts = new ArrayList<>();
                List<IExpr> constParts = new ArrayList<>();

                for (int i = 1; i < ast.size(); i++) {
                    if (containsVariable(ast.get(i), var)) {
                        varParts.add(ast.get(i));
                    } else {
                        constParts.add(ast.get(i));
                    }
                }

                if (!constParts.isEmpty() && !varParts.isEmpty()) {
                    IExpr constExpr = constParts.size() == 1 ? constParts.getFirst() : util.eval("Times(" + joinExprs(constParts) + ")");
                    IExpr varExpr = varParts.size() == 1 ? varParts.getFirst() : util.eval("Times(" + joinExprs(varParts) + ")");
                    steps.add("We notice there is a constant coefficient here. Let's pull out the constant factor $" + toLatex(constExpr) + "$ using the Constant Multiple Rule. This leaves us with: $$" + toLatex(constExpr) + " \\cdot \\frac{d}{d" + var + "}[" + toLatex(varExpr) + "]$$");
                    generateDerivativeSteps(varExpr, var, steps);
                } else if (ast.size() == 3) { // Product Rule for u * v
                    IExpr u = ast.get(1);
                    IExpr v = ast.get(2);
                    steps.add("We have a product of two terms, so we apply the Product Rule: $$(u \\cdot v)' = u \\cdot v' + v \\cdot u'$$ Let's write this out explicitly: $$\\frac{d}{d" + var + "}[" + toLatex(u) + " \\cdot " + toLatex(v) + "] = " + toLatex(u) + " \\cdot \\frac{d}{d" + var + "}[" + toLatex(v) + "] + " + toLatex(v) + " \\cdot \\frac{d}{d" + var + "}[" + toLatex(u) + "]$$ Now we'll find the derivative of each part.");
                    generateDerivativeSteps(u, var, steps);
                    generateDerivativeSteps(v, var, steps);
                }
            } else if (headName.equals("power")) {
                IExpr base = ast.get(1);
                IExpr exponent = ast.get(2);
                if (!containsVariable(exponent, var)) { // Power Rule: x^n
                    steps.add("For the term $" + toLatex(expr) + "$, we apply the Power Rule: bring the exponent down and subtract $1$ from the power. We also apply the Chain Rule to differentiate the inner expression: $$\\frac{d}{d" + var + "}[" + toLatex(expr) + "] = " + toLatex(exponent) + " \\cdot " + toLatex(base) + "^{" + toLatex(util.eval(exponent + " - 1")) + "} \\cdot \\frac{d}{d" + var + "}[" + toLatex(base) + "]$$");
                    if (containsVariable(base, var) && !base.isSymbol()) {
                        generateDerivativeSteps(base, var, steps);
                    }
                }
            } else if (headName.equals("sin") || headName.equals("cos") || headName.equals("tan") || headName.equals("exp") || headName.equals("log")) {
                IExpr arg = ast.get(1);
                steps.add("This is a composition of functions. We must apply the Chain Rule: differentiate the outer function first, evaluated at the inner function, then multiply by the derivative of the inner function. So, $$\\frac{d}{d" + var + "}[" + headName + "(u)] = " + headName + "'(u) \\cdot \\frac{d}{d" + var + "}[u]$$ where $u = " + toLatex(arg) + "$.");
                generateDerivativeSteps(arg, var, steps);
            }
        }
    }

    private boolean containsVariable(IExpr expr, String var) {
        if (expr.isSymbol()) {
            return expr.toString().equals(var);
        }
        if (expr.isAST()) {
            IAST ast = (IAST) expr;
            for (int i = 1; i < ast.size(); i++) {
                if (containsVariable(ast.get(i), var)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String joinExprs(List<IExpr> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).toString());
            if (i < list.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private SolveResponse handleIntegral(String input, List<String> steps) {
        String var = "x";
        String exprStr = input;
        
        // Normalize Integrate(x^2, x)
        if (exprStr.toLowerCase().startsWith("integrate(")) {
            int firstParen = exprStr.indexOf('(');
            int lastParen = exprStr.lastIndexOf(')');
            if (firstParen != -1 && lastParen != -1) {
                String contents = exprStr.substring(firstParen + 1, lastParen);
                String[] parts = contents.split(",");
                exprStr = parts[0].trim();
                if (parts.length > 1) {
                    var = parts[1].trim();
                }
            }
        } else if (exprStr.toLowerCase().startsWith("integrate") || exprStr.toLowerCase().startsWith("int")) {
            // Strip keywords
            exprStr = exprStr.replaceAll("(?i)integrate|int", "").trim();
            if (exprStr.endsWith("dx") || exprStr.endsWith("dy")) {
                var = exprStr.substring(exprStr.length() - 1);
                exprStr = exprStr.substring(0, exprStr.length() - 2).trim();
            }
        }

        IExpr parsedExpr = util.eval(exprStr);
        steps.add("We want to evaluate the indefinite integral: $$\\int " + toLatex(parsedExpr) + " \\, d" + var + "$$ Let's write down the problem on paper and solve it step-by-step.");
        
        // Dynamically walk the AST and generate textbook steps
        generateIntegralSteps(parsedExpr, var, steps);

        IExpr solved = util.eval("Integrate(" + exprStr + ", " + var + ")");
        String solvedLatex = toLatex(solved) + " + C";
        steps.add("Now, putting all the parts together and adding the constant of integration $C$, we get our final result: $$" + solvedLatex + "$$");

        return new SolveResponse(steps, solved.toString() + " + C", solvedLatex);
    }

    private void generateIntegralSteps(IExpr expr, String var, List<String> steps) {
        if (!containsVariable(expr, var)) {
            steps.add("First, we see that $" + toLatex(expr) + "$ is a constant with respect to $" + var + "$. We can integrate it directly: $$\\int " + toLatex(expr) + " \\, d" + var + " = " + toLatex(expr) + " \\cdot " + var + "$$");
            return;
        }

        if (expr.isSymbol()) {
            if (expr.toString().equals(var)) {
                steps.add("This is a simple power of $" + var + "$. We apply the Power Rule for integration, which tells us to increase the exponent by $1$ and divide by the new exponent: $$\\int " + var + " \\, d" + var + " = \\frac{" + var + "^2}{2}$$");
            }
            return;
        }

        if (expr.isAST()) {
            IAST ast = (IAST) expr;
            String headName = ast.head().toString().toLowerCase();

            if (headName.equals("plus")) {
                steps.add("Let's integrate this sum term-by-term. We can apply the Integration Sum Rule to split the integral into separate pieces: $$\\int (u + v) \\, d" + var + " = \\int u \\, d" + var + " + \\int v \\, d" + var + "$$");
                for (int i = 1; i < ast.size(); i++) {
                    generateIntegralSteps(ast.get(i), var, steps);
                }
            } else if (headName.equals("times")) {
                // Check constant multiple
                List<IExpr> varParts = new ArrayList<>();
                List<IExpr> constParts = new ArrayList<>();

                for (int i = 1; i < ast.size(); i++) {
                    if (containsVariable(ast.get(i), var)) {
                        varParts.add(ast.get(i));
                    } else {
                        constParts.add(ast.get(i));
                    }
                }

                if (!constParts.isEmpty() && !varParts.isEmpty()) {
                    IExpr constExpr = constParts.size() == 1 ? constParts.getFirst() : util.eval("Times(" + joinExprs(constParts) + ")");
                    IExpr varExpr = varParts.size() == 1 ? varParts.getFirst() : util.eval("Times(" + joinExprs(varParts) + ")");
                    steps.add("We can make this easier by pulling out the constant factor $" + toLatex(constExpr) + "$ using the Constant Multiple Rule: $$\\int " + toLatex(constExpr) + " \\cdot f(" + var + ") \\, d" + var + " = " + toLatex(constExpr) + " \\int f(" + var + ") \\, d" + var + "$$ Now we only need to focus on integrating the remaining function.");
                    generateIntegralSteps(varExpr, var, steps);
                } else if (ast.size() == 3) {
                    // Check for Integration by Parts: e.g. x * cos(a*x + b)
                    IExpr arg1 = ast.get(1);
                    IExpr arg2 = ast.get(2);
                    IExpr u = null;
                    IExpr dv = null;
                    
                    if (arg1.toString().equals(var) && arg2.isAST()) {
                        u = arg1;
                        dv = arg2;
                    } else if (arg2.toString().equals(var) && arg1.isAST()) {
                        u = arg2;
                        dv = arg1;
                    }
                    
                    if (u != null && dv != null) {
                        IAST dvAst = (IAST) dv;
                        String dvHeadName = dvAst.head().toString().toLowerCase();
                        if (dvHeadName.equals("cos") || dvHeadName.equals("sin") || dvHeadName.equals("exp")) {
                            steps.add("We have a product of algebraic and trigonometric/exponential terms, so we apply **Integration by Parts**. Let's write down the standard formula first: $$\\int u \\, dv = u \\cdot v - \\int v \\, du$$");
                            steps.add("Using the LIATE rule, we choose our parts: $$u = " + toLatex(u) + " \\implies du = d" + var + "$$");
                            
                            IExpr v = util.eval("Integrate(" + dv + ", " + var + ")");
                            steps.add("For the remaining part, we set: $$dv = " + toLatex(dv) + " \\, d" + var + " \\implies v = \\int " + toLatex(dv) + " \\, d" + var + " = " + toLatex(v) + "$$");
                            
                            steps.add("Now, we substitute $u$, $v$, and $du$ back into our Integration by Parts formula: $$\\int " + toLatex(expr) + " \\, d" + var + " = " + toLatex(u) + " \\cdot \\left(" + toLatex(v) + "\\right) - \\int \\left(" + toLatex(v) + "\\right) \\, d" + var + "$$");
                            
                            // Integrate v
                            IExpr remainingIntegral = util.eval("Integrate(" + v + ", " + var + ")");
                            steps.add("Let's calculate the remaining integral on the right: $$\\int " + toLatex(v) + " \\, d" + var + " = " + toLatex(remainingIntegral) + "$$");
                        }
                    }
                }
            } else if (headName.equals("power")) {
                IExpr base = ast.get(1);
                IExpr exponent = ast.get(2);
                if (base.toString().equals(var) && !containsVariable(exponent, var)) {
                    steps.add("This is in the form of a power function. We apply the Power Rule: $$\\int " + var + "^{n} \\, d" + var + " = \\frac{" + var + "^{n+1}}{n+1}$$ for $n \\neq -1$. Here $n = " + toLatex(exponent) + "$, so we get: $$\\int " + toLatex(expr) + " \\, d" + var + " = \\frac{" + var + "^{" + toLatex(util.eval(exponent + "+1")) + "}}{" + toLatex(util.eval(exponent + "+1")) + "}$$");
                }
            } else if (headName.equals("cos")) {
                IExpr arg = ast.get(1);
                IExpr deriv = util.eval("D(" + arg + ", " + var + ")");
                steps.add("We integrate the cosine function. Since the inner expression is linear, we can use a quick substitution (reverse chain rule), applying the Cosine Integration Rule: $$\\int \\cos(ax+b) \\, dx = \\frac{\\sin(ax+b)}{a}$$ This gives: $$\\int \\cos(" + toLatex(arg) + ") \\, d" + var + " = \\frac{\\sin(" + toLatex(arg) + ")}{" + toLatex(deriv) + "}$$");
            } else if (headName.equals("sin")) {
                IExpr arg = ast.get(1);
                IExpr deriv = util.eval("D(" + arg + ", " + var + ")");
                steps.add("We integrate the sine function. Since the inner expression is linear, we apply the Sine Integration Rule: $$\\int \\sin(ax+b) \\, dx = -\\frac{\\cos(ax+b)}{a}$$ This gives: $$\\int \\sin(" + toLatex(arg) + ") \\, d" + var + " = -\\frac{\\cos(" + toLatex(arg) + ")}{" + toLatex(deriv) + "}$$");
            }
        }
    }

    private SolveResponse handleLimit(String input, List<String> steps) {
        String var = "x";
        String value = "0";
        String exprStr = input;

        // Pattern Limit(x^2, x, 2)
        if (exprStr.toLowerCase().startsWith("limit(")) {
            int firstParen = exprStr.indexOf('(');
            int lastParen = exprStr.lastIndexOf(')');
            if (firstParen != -1 && lastParen != -1) {
                String contents = exprStr.substring(firstParen + 1, lastParen);
                String[] parts = contents.split(",");
                exprStr = parts[0].trim();
                if (parts.length > 1) var = parts[1].trim();
                if (parts.length > 2) value = parts[2].trim();
            }
        }

        IExpr parsedExpr = util.eval(exprStr);
        steps.add("Evaluate the limit: $$\\lim_{" + var + " \\to " + value + "} " + toLatex(parsedExpr) + "$$");
        
        // Try direct substitution first
        try {
            IExpr subbed = util.eval("ReplaceAll(" + exprStr + ", Rule(" + var + ", " + value + "))");
            steps.add("Substitute $" + var + " = " + value + "$ directly into the expression:");
            steps.add("$$\\lim_{" + var + " \\to " + value + "} " + toLatex(parsedExpr) + " = " + toLatex(subbed) + "$$");
        } catch (Exception e) {
            steps.add("Attempt factoring or simplifying the expression to resolve undefined points.");
        }

        IExpr solved = util.eval("Limit(" + exprStr + ", " + var + " -> " + value + ")");
        String solvedLatex = toLatex(solved);
        steps.add("The limit evaluates to: $$" + solvedLatex + "$$");

        return new SolveResponse(steps, solved.toString(), solvedLatex);
    }

    private SolveResponse handleAlgebra(String input, List<String> steps) {
        // Look for equations containing '='
        String lhs = input;
        String rhs = "0";
        if (input.contains("=")) {
            String[] split = input.split("=");
            lhs = split[0].trim();
            rhs = split[1].trim();
        }

        steps.add("Solve the algebraic equation: $$" + toLatex(util.eval(lhs)) + " = " + toLatex(util.eval(rhs)) + "$$");
        steps.add("Move all terms to one side: $$" + toLatex(util.eval(lhs + " - (" + rhs + ")")) + " = 0$$");

        // Try solving using Symja's Solve
        IExpr solved = util.eval("Solve(" + lhs + " == " + rhs + ", x)");
        String solvedLatex = toLatex(solved);
        steps.add("Solving for $x$, we find: $$" + solvedLatex + "$$");

        return new SolveResponse(steps, solved.toString(), solvedLatex);
    }

    private String toLatex(IExpr expr) {
        StringBuilder sb = new StringBuilder();
        texFactory.convert(sb, expr, 0);
        return sb.toString();
    }
}
