/**
 * SCALA LICENSE
 *
 * Copyright (c) 2002-2013 EPFL, Lausanne, unless otherwise specified.
 * All rights reserved.
 *
 * This software was developed by the Programming Methods Laboratory of the
 * Swiss Federal Institute of Technology (EPFL), Lausanne, Switzerland.
 *
 * Permission to use, copy, modify, and distribute this software in source
 * or binary form for any purpose with or without fee is hereby granted,
 * provided that the following conditions are met:
 *
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the EPFL nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

/*
 * Why this code is here? Because someone very smart mark parser traits as 'sealed'.
 * Inheritance is prohibited. So the only way is copy'n'paste.
 *   Ezh.
 */

package org.digimead.tabuddy.desktop.core.definition.command

/** `JavaTokenParsers` differs from [[scala.util.parsing.combinator.RegexParsers]]
 *  by adding the following definitions:
 *
 *  - `ident`
 *  - `wholeNumber`
 *  - `decimalNumber`
 *  - `stringLiteral`
 *  - `floatingPointNumber`
 */
trait JavaTokenParsers extends RegexParsers {
  /** Anything that is a valid Java identifier, according to
   * <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">The Java Language Spec</a>.
   * Generally, this means a letter, followed by zero or more letters or numbers.
   */
  def ident: Parser[String] =
    """\p{javaJavaIdentifierStart}\p{javaJavaIdentifierPart}*""".r
  /** An integer, without sign or with a negative sign. */
  def wholeNumber: Parser[String] =
    """-?\d+""".r
  /** Number following one of these rules:
   *
   *  - An integer. For example: `13`
   *  - An integer followed by a decimal point. For example: `3.`
   *  - An integer followed by a decimal point and fractional part. For example: `3.14`
   *  - A decimal point followed by a fractional part. For example: `.1`
   */
  def decimalNumber: Parser[String] =
    """(\d+(\.\d*)?|\d*\.\d+)""".r
  /** Double quotes (`"`) enclosing a sequence of:
   *
   *  - Any character except double quotes, control characters or backslash (`\`)
   *  - A backslash followed by another backslash, a single or double quote, or one
   *    of the letters `b`, `f`, `n`, `r` or `t`
   *  - `\` followed by `u` followed by four hexadecimal digits
   */
  def stringLiteral: Parser[String] =
    ("\""+"""([^"\p{Cntrl}\\]|\\[\\'"bfnrt]|\\u[a-fA-F0-9]{4})*"""+"\"").r
  /** A number following the rules of `decimalNumber`, with the following
   *  optional additions:
   *
   *  - Preceded by a negative sign
   *  - Followed by `e` or `E` and an optionally signed integer
   *  - Followed by `f`, `f`, `d` or `D` (after the above rule, if both are used)
   */
  def floatingPointNumber: Parser[String] =
    """-?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r
}
