package com.indeci.rrhh.subsidio.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;

/**
 * Motor de fórmulas JSON seguro para subsidios — sin SpEL/Groovy/SQL dinámico.
 */
@Component
public class SubsidioFormulaEngine {

    private final ObjectMapper objectMapper;

    public SubsidioFormulaEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BigDecimal evaluar(String expresionJson, SubsidioFormulaContext ctx) {
        if (expresionJson == null || expresionJson.isBlank()) {
            throw new NegocioException("Expresión de fórmula vacía");
        }
        try {
            JsonNode root = objectMapper.readTree(expresionJson);
            return evalNode(root, ctx);
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("Fórmula de subsidio inválida: " + ex.getMessage());
        }
    }

    private BigDecimal evalNode(JsonNode node, SubsidioFormulaContext ctx) {
        if (node.has("ref")) {
            return ctx.ref(node.get("ref").asText());
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String op = node.has("op") ? node.get("op").asText() : null;
        if (op == null) {
            throw new NegocioException("Nodo de fórmula sin operador");
        }
        BigDecimal result = switch (op) {
            case "ADD", "+" -> sumArgs(node, ctx);
            case "SUBTRACT", "-" -> subtractArgs(node, ctx);
            case "MULTIPLY", "*" -> multiplyArgs(node, ctx);
            case "DIVIDE", "/" -> divideArgs(node, ctx);
            case "MIN" -> minArgs(node, ctx);
            case "MAX" -> maxArgs(node, ctx);
            case "TOP" -> topArg(node, ctx);
            case "ROUND" -> roundArg(node, ctx);
            case "SUM_TOP" -> sumTop(node, ctx);
            default -> throw new NegocioException("Operador no permitido: " + op);
        };
        if (node.has("round")) {
            return applyRound(result, node.get("round"));
        }
        return result;
    }

    private BigDecimal sumTop(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal sum = BigDecimal.ZERO;
        for (SubsidioBaseDetalle det : ctx.baseDetalles()) {
            BigDecimal monto = det.getBaseComputable() != null
                    ? det.getBaseComputable() : BigDecimal.ZERO;
            if (ctx.topeMensual() != null && monto.compareTo(ctx.topeMensual()) > 0) {
                monto = ctx.topeMensual();
            }
            sum = sum.add(monto);
        }
        int meses = node.has("months") ? node.get("months").asInt() : ctx.mesesEvaluados();
        if (ctx.baseDetalles().isEmpty() && meses > 0) {
            return BigDecimal.ZERO;
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal topArg(JsonNode node, SubsidioFormulaContext ctx) {
        Iterator<JsonNode> it = node.get("args").iterator();
        BigDecimal valor = evalNode(it.next(), ctx);
        BigDecimal cap = ctx.topeMensual() != null ? ctx.topeMensual() : valor;
        return valor.min(cap);
    }

    private BigDecimal roundArg(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal valor = evalNode(node.get("args").get(0), ctx);
        return applyRound(valor, node);
    }

    private BigDecimal applyRound(BigDecimal valor, JsonNode roundNode) {
        String mode = roundNode.has("mode") ? roundNode.get("mode").asText() : "HALF_UP";
        int scale = roundNode.has("scale") ? roundNode.get("scale").asInt() : 2;
        RoundingMode rm = RoundingMode.valueOf(mode);
        return valor.setScale(scale, rm);
    }

    private BigDecimal sumArgs(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal acc = BigDecimal.ZERO;
        for (JsonNode arg : node.get("args")) {
            acc = acc.add(evalNode(arg, ctx));
        }
        return acc;
    }

    private BigDecimal subtractArgs(JsonNode node, SubsidioFormulaContext ctx) {
        Iterator<JsonNode> it = node.get("args").iterator();
        BigDecimal acc = evalNode(it.next(), ctx);
        while (it.hasNext()) {
            acc = acc.subtract(evalNode(it.next(), ctx));
        }
        return acc;
    }

    private BigDecimal multiplyArgs(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal acc = BigDecimal.ONE;
        for (JsonNode arg : node.get("args")) {
            acc = acc.multiply(evalNode(arg, ctx));
        }
        return acc;
    }

    private BigDecimal divideArgs(JsonNode node, SubsidioFormulaContext ctx) {
        Iterator<JsonNode> it = node.get("args").iterator();
        BigDecimal num = evalNode(it.next(), ctx);
        BigDecimal den = evalNode(it.next(), ctx);
        if (den.signum() == 0) {
            throw new NegocioException("División por cero en fórmula de subsidio");
        }
        return num.divide(den, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal minArgs(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal min = null;
        for (JsonNode arg : node.get("args")) {
            BigDecimal v = evalNode(arg, ctx);
            min = min == null ? v : min.min(v);
        }
        return min != null ? min : BigDecimal.ZERO;
    }

    private BigDecimal maxArgs(JsonNode node, SubsidioFormulaContext ctx) {
        BigDecimal max = null;
        for (JsonNode arg : node.get("args")) {
            BigDecimal v = evalNode(arg, ctx);
            max = max == null ? v : max.max(v);
        }
        return max != null ? max : BigDecimal.ZERO;
    }
}
