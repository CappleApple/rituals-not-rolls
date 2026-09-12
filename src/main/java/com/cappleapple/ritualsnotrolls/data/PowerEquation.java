package com.cappleapple.ritualsnotrolls.data;

import java.util.Set;

/** Small arithmetic expressions for balance settings; no scripting or external state. */
public final class PowerEquation {
  public static final String DUPLICATES = "1 / sqrt(n)";
  public static final String ENCHANTABILITY = "base * (1 + log(rating / base))";

  @FunctionalInterface
  public interface Expression {
    double evaluate(double value, double base);
  }

  private record Cached(String source, Expression expression) {}

  private static volatile Cached duplicates = new Cached(DUPLICATES, compile(DUPLICATES, "n"));
  private static volatile Cached enchantability =
      new Cached(ENCHANTABILITY, compile(ENCHANTABILITY, "rating"));

  public static double duplicateFactor(String equation, int copy) {
    var cached = duplicates;
    if (!cached.source().equals(equation))
      duplicates = cached = new Cached(equation, compile(equation, "n"));
    double value = cached.expression().evaluate(Math.max(1, copy), 1);
    return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 1 / Math.sqrt(Math.max(1, copy));
  }

  public static double effectiveEnchantability(String equation, double rating, double base) {
    rating = Math.max(1, rating);
    if (rating <= base) return rating;
    var cached = enchantability;
    if (!cached.source().equals(equation))
      enchantability = cached = new Cached(equation, compile(equation, "rating"));
    double value = cached.expression().evaluate(rating, base);
    return Double.isFinite(value)
        ? Math.clamp(value, base, rating)
        : base * (1 + Math.log(rating / base));
  }

  public static boolean valid(Object value, String variable) {
    if (!(value instanceof String source)) return false;
    try {
      var expression = compile(source, variable);
      for (double base : new double[] {1, 10, 100})
        for (double input : new double[] {1, 2, 3, 10, 25, 100, 4096, Integer.MAX_VALUE}) {
          double result =
              expression.evaluate(variable.equals("rating") ? base + input : input, base);
          if (!Double.isFinite(result) || result < 0) return false;
        }
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public static Expression compile(String source, String variable) {
    if (source.isBlank() || source.length() > 256)
      throw new IllegalArgumentException("Power equation must contain 1-256 characters");
    var parser = new Parser(source, variable);
    var result = parser.sum();
    parser.whitespace();
    if (parser.index != source.length()) throw parser.error();
    return result;
  }

  private static final class Parser {
    private final String source, variable;
    private int index;

    Parser(String source, String variable) {
      this.source = source;
      this.variable = variable;
    }

    IllegalArgumentException error() {
      return new IllegalArgumentException("Invalid power equation at character " + (index + 1));
    }

    void whitespace() {
      while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
    }

    boolean take(char token) {
      whitespace();
      if (index < source.length() && source.charAt(index) == token) {
        index++;
        return true;
      }
      return false;
    }

    void require(char token) {
      if (!take(token)) throw error();
    }

    Expression sum() {
      var result = product();
      while (true) {
        var left = result;
        if (take('+')) {
          var right = product();
          result = (v, b) -> left.evaluate(v, b) + right.evaluate(v, b);
        } else if (take('-')) {
          var right = product();
          result = (v, b) -> left.evaluate(v, b) - right.evaluate(v, b);
        } else return result;
      }
    }

    Expression product() {
      var result = unary();
      while (true) {
        var left = result;
        if (take('*')) {
          var right = unary();
          result = (v, b) -> left.evaluate(v, b) * right.evaluate(v, b);
        } else if (take('/')) {
          var right = unary();
          result = (v, b) -> left.evaluate(v, b) / right.evaluate(v, b);
        } else return result;
      }
    }

    Expression unary() {
      if (take('+')) return unary();
      if (take('-')) {
        var operand = unary();
        return (v, b) -> -operand.evaluate(v, b);
      }
      var left = atom();
      if (!take('^')) return left;
      var right = unary();
      return (v, b) -> Math.pow(left.evaluate(v, b), right.evaluate(v, b));
    }

    Expression atom() {
      if (take('(')) {
        var result = sum();
        require(')');
        return result;
      }
      whitespace();
      int start = index;
      while (index < source.length()
          && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) index++;
      if (index > start) {
        double number = Double.parseDouble(source.substring(start, index));
        if (!Double.isFinite(number)) throw error();
        return (v, b) -> number;
      }
      while (index < source.length() && Character.isLetter(source.charAt(index))) index++;
      String name = source.substring(start, index);
      if (name.equals(variable)) return (v, b) -> v;
      if (name.equals("base") && variable.equals("rating")) return (v, b) -> b;
      if (!Set.of("sqrt", "log", "min", "max", "pow").contains(name)) throw error();
      require('(');
      var first = sum();
      if (name.equals("sqrt") || name.equals("log")) {
        require(')');
        return name.equals("sqrt")
            ? (v, b) -> Math.sqrt(first.evaluate(v, b))
            : (v, b) -> Math.log(first.evaluate(v, b));
      }
      require(',');
      var second = sum();
      require(')');
      return switch (name) {
        case "min" -> (v, b) -> Math.min(first.evaluate(v, b), second.evaluate(v, b));
        case "max" -> (v, b) -> Math.max(first.evaluate(v, b), second.evaluate(v, b));
        default -> (v, b) -> Math.pow(first.evaluate(v, b), second.evaluate(v, b));
      };
    }
  }
}
