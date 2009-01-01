/* LanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool.rules.patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.danielnaber.languagetool.AnalyzedToken;
import de.danielnaber.languagetool.AnalyzedTokenReadings;
import de.danielnaber.languagetool.JLanguageTool;
import de.danielnaber.languagetool.synthesis.Synthesizer;
import de.danielnaber.languagetool.tools.StringTools;

/**
 * A part of a pattern.
 * 
 * @author Daniel Naber
 */
public class Element {

  private String stringToken;
  private String posToken;
  private String regToken;
  private boolean posRegExp;

  private boolean negation;
  private boolean posNegation;

  final private boolean caseSensitive;
  final private boolean stringRegExp;
  private boolean inflected;

  private boolean testWhitespace;
  private boolean whitespaceBefore;

  /**
   * List of exceptions that are valid for the current token and / or some next
   * tokens.
   */
  private List<Element> exceptionList;

  /**
   * True if scope=="next".
   */
  private boolean exceptionValidNext;

  /**
   * True if any exception with a scope=="current" or scope=="next" is set for
   * the element.
   */
  private boolean exceptionSet;

  /**
   * True if attribute scope=="previous".
   */
  private boolean exceptionValidPrevious;

  /**
   * List of exceptions that are valid for a previous token.
   */
  private List<Element> previousExceptionList;

  private List<Element> andGroupList;
  private boolean andGroupSet;
  private boolean[] andGroupCheck;

  private int skip;

  private Pattern p;
  private Pattern pPos;

  private Matcher m;
  private Matcher mPos;

  /** The reference to another element in the pattern. **/
  private Match tokenReference;

  /**
   * True when the element stores a formatted reference to another element of
   * the pattern.
   */
  private boolean containsMatches;

  /** Matches only tokens without any POS tag. **/
  private static final String UNKNOWN_TAG = "UNKNOWN";

  /**
   * Parameter passed to regular expression matcher to enable case insensitive
   * Unicode matching.
   */
  private static final String CASE_INSENSITIVE = "(?iu)";

  private String referenceString;

  /** String ID of the phrase the element is in. **/
  private String phraseName;

  /**
   * This var is used to determine if calling {@link #matchStringToken()} makes
   * sense. This method takes most time so it's best reduce the number of its
   * calls.
   **/
  private boolean testString;

  /**
   * Tells if the element is inside the unification, so that {@link Unifier}
   * tests it.
   */
  private boolean unified;
  private boolean uniNegation;

  private String unificationFeature;
  private String unificationType;

  /**
   * Creates Element that is used to match tokens in the text.
   * 
   * @param token
   *          String to be matched
   * @param caseSensitive
   *          True if the check is case-sensitive.
   * @param regExp
   *          True if the check uses regular expressions.
   * @param inflected
   *          True if the check refers to base forms (lemmas).
   */
  public Element(final String token, final boolean caseSensitive,
      final boolean regExp, final boolean inflected) {
    this.caseSensitive = caseSensitive;
    this.stringRegExp = regExp;
    this.inflected = inflected;
    setStringElement(token);
  }

  /**
   * Checks whether the rule element matches the token given as a parameter.
   * 
   * @param token
   * @AnalyzedToken to check matching against
   * @return True if token matches, false otherwise.
   */
  public final boolean isMatched(final AnalyzedToken token) {
    boolean matched = false;
    if (testString) {
      matched = (isStringTokenMatched(token) ^ negation)
          && (isPosTokenMatched(token) ^ posNegation)
          && (!testWhitespace || isWhitespaceBefore(token));
    } else {
      matched = (!negation) && (isPosTokenMatched(token) ^ posNegation);
    }

    if (andGroupSet) {
      andGroupCheck[0] |= matched;
    }

    return matched;
  }

  /**
   * Checks whether an exception matches.
   * 
   * @param token
   * @AnalyzedToken to check matching against
   * @return True if any of the exceptions matches (logical disjunction).
   */
  public final boolean isExceptionMatched(final AnalyzedToken token) {
    boolean exceptionMatched = false;
    if (exceptionSet) {
      for (final Element testException : exceptionList) {
        if (!testException.exceptionValidNext) {
          exceptionMatched |= testException.isMatched(token);
        }
        if (exceptionMatched) {
          break;
        }
      }
    }
    return exceptionMatched;
  }

  /**
   * Enables testing multiple conditions specified by different elements.
   * Doesn't test exceptions.
   * 
   * Works as logical AND operator only if preceded with
   * {@link #setupAndGroup()}, and followed by {@link #checkAndGroup(boolean)}.
   * 
   * @param token
   *          AnalyzedToken - the token checked.
   * @return true if any condition is met, false otherwise.
   */
  public final boolean isAndGroupMatched(final AnalyzedToken token) {
    boolean andGroupMatched = false;
    if (andGroupSet) {
      for (int i = 0; i < andGroupList.size(); i++) {
        if (!andGroupCheck[i + 1]) {
          final Element testAndGroup = andGroupList.get(i);
          if (testAndGroup.isMatched(token)) {
            andGroupMatched = true;
            andGroupCheck[i + 1] = true;
          }
        }
      }
    }
    return andGroupMatched;
  }

  public final void setupAndGroup() {
    if (andGroupSet) {
      andGroupCheck = new boolean[andGroupList.size() + 1];
      for (int i = 0; i < andGroupList.size(); i++) {
        andGroupCheck[i] = false;
      }
    }
  }

  public final boolean checkAndGroup(final boolean previousValue) {
    if (andGroupSet) {
      boolean allConditionsMatch = true;
      for (final boolean testValue : andGroupCheck) {
        allConditionsMatch &= testValue;
      }
      return allConditionsMatch;
    }
    return previousValue;
  }

  /**
   * This method checks AND-group and the token. Introduced to for clarity.
   * 
   * @param token
   *          Token to match
   * @return True if matched.
   */
  public final boolean isMatchedCompletely(final AnalyzedToken token) {
    // note: do not use "||" here, we need full evaluation, no short-circuiting
    return isMatched(token) | isAndGroupMatched(token);
  }

  /**
   * Enables testing multiple conditions specified by multiple element
   * exceptions.
   * 
   * Works as logical AND operator.
   * 
   * @param token
   *          AnalyzedToken - the token checked for exceptions.
   * @return true if all conditions are met, false otherwise.
   */
  public final boolean isAndExceptionGroupMatched(final AnalyzedToken token) {
    boolean andGroupExceptionMatched = false;
    if (andGroupSet) {
      for (final Element testAndGroup : andGroupList) {
        andGroupExceptionMatched |= testAndGroup.isExceptionMatched(token);
        if (andGroupExceptionMatched) {
          return andGroupExceptionMatched;
        }
      }
    }
    return andGroupExceptionMatched;
  }

  /**
   * This method checks exceptions both in AND-group and the token. Introduced
   * to for clarity.
   * 
   * @param token
   *          Token to match
   * @return True if matched.
   */
  public final boolean isExceptionMatchedCompletely(final AnalyzedToken token) {
    // note: short-circuiting possible
    return isExceptionMatched(token) || isAndExceptionGroupMatched(token);
  }

  public final void setAndGroupElement(final Element andToken) {
    if (andToken != null) {
      if (andGroupList == null) {
        andGroupList = new ArrayList<Element>();
      }
      if (!andGroupSet) {
        andGroupSet = true;
      }
      andGroupList.add(andToken);
    }
  }

  /**
   * Checks if this element has an AND group associated with it.
   * 
   * @return true if the element has a group of elements that all should match.
   */
  public final boolean hasAndGroup() {
    return andGroupSet;
  }

  /**
   * Returns the group of elements linked with AND operator.
   * 
   * @return List of Elements.
   */
  public final List<Element> getAndGroup() {
    return andGroupList;
  }

  /**
   * Checks whether a previously set exception matches (in case the exception
   * had scope == "next").
   * 
   * @param token
   * @AnalyzedToken to check matching against.
   * @return True if any of the exceptions matches.
   */
  public final boolean isMatchedByScopeNextException(final AnalyzedToken token) {
    boolean exceptionMatched = false;
    if (exceptionSet) {
      for (final Element testException : exceptionList) {
        if (testException.exceptionValidNext) {
          exceptionMatched |= testException.isMatched(token);
        }
        if (exceptionMatched) {
          break;
        }
      }
    }
    return exceptionMatched;
  }

  /**
   * Checks whether an exception for a previous token matches (in case the
   * exception had scope == "previous").
   * 
   * @param token
   * @AnalyzedToken to check matching against.
   * @return True if any of the exceptions matches.
   */
  public final boolean isMatchedByScopePreviousException(
      final AnalyzedToken token) {
    boolean exceptionMatched = false;
    if (exceptionValidPrevious) {
      for (final Element testException : previousExceptionList) {
        if (!testException.exceptionValidNext) {
          exceptionMatched |= testException.isMatched(token);
        }
        if (exceptionMatched) {
          break;
        }
      }
    }
    return exceptionMatched;
  }

  /**
   * Checks if the token is a SENT_START.
   * 
   * @return True if the token starts the sentence.
   */
  public final boolean isSentStart() {
    boolean equals = false;
    if (posToken != null) {
      equals = JLanguageTool.SENTENCE_START_TAGNAME.equals(posToken)
          && posNegation;
    }
    return equals;
  }

  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder();
    if (negation) {
      sb.append("!");
    }
    sb.append(stringToken);
    if (phraseName != null) {
      sb.append(" {");
      sb.append(phraseName);
      sb.append("}");
    }
    if (posToken != null) {
      sb.append("/");
      sb.append(posToken);
    }
    return sb.toString();
  }

  public final void setPosElement(final String posToken, final boolean regExp,
      final boolean negation) {
    this.posToken = posToken;
    this.posNegation = negation;
    posRegExp = regExp;
    if (posRegExp) {
      pPos = Pattern.compile(posToken);
    }
  }

  public final String getString() {
    return stringToken;
  }

  public final void setStringElement(final String token) {
    this.stringToken = token;
    testString = true;
    if (StringTools.isEmpty(stringToken)) {
      testString = false;
    }
    if (testString && stringRegExp) {
      regToken = stringToken;
      if (!caseSensitive) {
        regToken = CASE_INSENSITIVE + stringToken;
      }
      p = Pattern.compile(regToken);
    }
  }

  /**
   * Sets a POS-type exception for matching string tokens.
   * 
   * @param posToken
   *          The part of the speech tag in the exception.
   * @param regExp
   *          True if the POS is specified as a regular expression.
   * @param negation
   *          True if the exception is negated.
   * @param scopeNext
   *          True if the exception scope is next tokens.
   * @param scopePrevious
   *          True if the exception should match only a single previous token.
   */
  public final void setPosException(final String posToken,
      final boolean regExp, final boolean negation, final boolean scopeNext,
      final boolean scopePrevious) {
    final Element posException = new Element("", this.caseSensitive, regExp,
        false);
    posException.setPosElement(posToken, regExp, negation);
    posException.exceptionValidNext = scopeNext;
    exceptionValidPrevious |= scopePrevious;
    if (exceptionList == null && !scopePrevious) {
      exceptionList = new ArrayList<Element>();
    }
    if (previousExceptionList == null && scopePrevious) {
      previousExceptionList = new ArrayList<Element>();
    }
    if (!(exceptionSet || scopePrevious)) {
      exceptionSet = true;
    }
    if (exceptionSet && !scopePrevious) {
      exceptionList.add(posException);
    }
    if (scopePrevious) {
      previousExceptionList.add(posException);
    }
  }

  /**
   * Sets a string-type exception for matching string tokens.
   * 
   * @param token
   *          The string in the exception.
   * @param regExp
   *          True if the string is specified as a regular expression.
   * @param inflected
   *          True if the string is a base form (lemma).
   * @param negation
   *          True if the exception is negated.
   * @param scopeNext
   *          True if the exception scope is next tokens.
   * @param scopePrevious
   *          True if the exception should match only a single previous token.
   */
  public final void setStringException(final String token,
      final boolean regExp, final boolean inflected, final boolean negation,
      final boolean scopeNext, final boolean scopePrevious) {
    final Element stringException = new Element(token, this.caseSensitive,
        regExp, inflected);
    stringException.setNegation(negation);
    stringException.exceptionValidNext = scopeNext;
    exceptionValidPrevious |= scopePrevious;
    if (exceptionList == null && !scopePrevious) {
      exceptionList = new ArrayList<Element>();
    }
    if (previousExceptionList == null && scopePrevious) {
      previousExceptionList = new ArrayList<Element>();
    }
    if (!(exceptionSet || scopePrevious)) {
      exceptionSet = true;
    }
    if (exceptionSet && !scopePrevious) {
      exceptionList.add(stringException);
    }
    if (scopePrevious) {
      previousExceptionList.add(stringException);
    }
  }

  /**
   * Tests if part of speech matches a given string.
   * 
   * @param token
   *          Token to test.
   * @return true if matches
   * 
   *         Special value UNKNOWN_TAG matches null POS tags.
   * 
   */
  private boolean isPosTokenMatched(final AnalyzedToken token) {
    // if no POS set
    // defaulting to true
    if (posToken == null) {
      return true;
    }
    if (token.getPOSTag() == null) {
      if (posRegExp) {
        if (mPos == null) {
          mPos = pPos.matcher(UNKNOWN_TAG);
        } else {
          mPos.reset(UNKNOWN_TAG);
        }
        return mPos.matches();
      }
      if (UNKNOWN_TAG.equals(posToken)) {
        return true;
      }
    }
    boolean match = false;
    if (posRegExp) {
      if (mPos == null) {
        mPos = pPos.matcher(token.getPOSTag());
      } else {
        mPos.reset(token.getPOSTag());
      }
      match = mPos.matches();
    } else {
      match = posToken.equals(token.getPOSTag());
    }
    return match;
  }

  /**
   * Tests whether the string token element matches a given token.
   * 
   * @param token
   * @AnalyzedToken to match against.
   * @return True if matches.
   */
  private boolean isStringTokenMatched(final AnalyzedToken token) {
    String testToken = null;
    // enables using words with lemmas and without lemmas
    // in the same regexp with inflected="yes"
    if (inflected) {
      testToken = token.getLemma();
      if (testToken == null) {
        testToken = token.getToken();
      }
    } else {
      testToken = token.getToken();
    }

    if (stringRegExp) {
      if (testToken != null) {
        if (m == null) {
          m = p.matcher(testToken);
        } else {
          m.reset(testToken);
        }
        return m.matches();
      }
    } else {
      if (caseSensitive) {
        return stringToken.equals(testToken);
      }
      return stringToken.equalsIgnoreCase(testToken);
    }
    return false;
  }

  /**
   * Gets the exception scope length.
   * 
   * @return @int Scope length.
   */
  public final int getSkipNext() {
    return skip;
  }

  /**
   * Sets the exception scope length.
   * 
   * @param i
   * @int Exception scope length.
   */
  public final void setSkipNext(final int i) {
    skip = i;
  }

  /**
   * Checks if the element has an exception for a previous token.
   * 
   * @return True if the element has a previous token matching exception.
   */
  public final boolean hasPreviousException() {
    return exceptionValidPrevious;
  }

  /**
   * Negates the meaning of match().
   * 
   * @param negation
   *          - true if the meaning of match() is to be negated.
   */
  public final void setNegation(final boolean negation) {
    this.negation = negation;
  }

  /**
   * see {@link #setNegation}
   * 
   * @since 0.9.3
   */
  public final boolean getNegation() {
    return this.negation;
  }

  /**
   * 
   * @return true when this element refers to another token.
   */
  public final boolean isReferenceElement() {
    return containsMatches;
  }

  /**
   * Sets the reference to another token.
   * 
   * @param match
   *          Formatting object for the token reference.
   */
  public final void setMatch(final Match match) {
    tokenReference = match;
    containsMatches = true;
  }

  public final Match getMatch() {
    return tokenReference;
  }

  /**
   * Prepare Element for matching by formatting its string token and POS (if the
   * Element is supposed to refer to some other token).
   * 
   * @param token
   * @AnalyzedTokenReadings - the token
   * @param synth
   * @Syntesizer - the language synthesizer
   * 
   */
  public final void compile(final AnalyzedTokenReadings token,
      final Synthesizer synth) {

    tokenReference.setToken(token);
    tokenReference.setSynthesizer(synth);

    if (StringTools.isEmpty(referenceString)) {
      referenceString = stringToken;
    }
    if (tokenReference.setsPos()) {
      final String posReference = tokenReference.getTargetPosTag();
      if (posReference != null) {
        if (mPos != null) {
          mPos = null;
        }
        setPosElement(posReference, tokenReference.posRegExp(), negation);
      }
      setStringElement(referenceString.replace("\\"
          + tokenReference.getTokenRef(), ""));
      inflected = true;
    } else {
      setStringElement(referenceString.replace("\\"
          + tokenReference.getTokenRef(), tokenReference.toTokenString()));
    }
  }

  /**
   * Sets the phrase the element is in.
   * 
   * @param s
   *          ID of the phrase.
   */
  public final void setPhraseName(final String s) {
    phraseName = s;
  }

  /**
   * Checks if the Element is in any phrase.
   * 
   * @return True if the Element is contained in the phrase.
   */
  public final boolean isPartOfPhrase() {
    return phraseName != null;
  }

  /**
   * Whether the element matches case sensitively.
   * 
   * @since 0.9.3
   */
  public final boolean getCaseSensitive() {
    return caseSensitive;
  }

  /**
   * Tests whether the element matches a regular expression.
   * 
   * @since 0.9.6
   */
  public final boolean isRegularExpression() {
    return stringRegExp;
  }

  /**
   * @return the POS of the Element
   * @since 0.9.6
   */
  public final String getPOStag() {
    return posToken;
  }

  /**
   * Tests whether the POS is negated.
   * 
   * @return true if so.
   */
  public final boolean getPOSNegation() {
    return posNegation;
  }

  /**
   * Whether the token is inflected.
   * 
   * @return True if so.
   */
  public final boolean isInflected() {
    return inflected;
  }

  /**
   * Gets the phrase the element is in.
   * 
   * @return String The name of the phrase.
   */
  public final String getPhraseName() {
    return phraseName;
  }

  public final boolean isUnified() {
    return unified;
  }

  public final void setUnification(final String feature, final String type) {
    unificationFeature = feature;
    unificationType = type;
    unified = true;
  }

  public final String getUniFeature() {
    return unificationFeature;
  }

  public final String getUniType() {
    return unificationType;
  }

  public final void setUniNegation() {
    uniNegation = true;
  }

  public final boolean getUniNegation() {
    return uniNegation;
  }

  public void setWhitespaceBefore(final boolean isWhite) {
    whitespaceBefore = isWhite;
    testWhitespace = true;
  }

  public void setExceptionSpaceBefore(final boolean isWhite) {
    if (exceptionList != null) {
      exceptionList.get(exceptionList.size()).setWhitespaceBefore(isWhite);
    }
  }

  public boolean isWhitespaceBefore(final AnalyzedToken token) {
    return (whitespaceBefore == token.isWhitespaceBefore());
  }
}
