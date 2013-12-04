/* LanguageTool, a natural language style checker
 * Copyright (C) 2013 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool.dev.wikipedia.atom;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.languagetool.rules.RuleMatch;

import java.util.Date;
import java.util.Objects;

/**
 * A wrapper around a {@link RuleMatch} with an {@link Object#equals(Object)} implementation
 * that only considers the rule id. This is done so the diff algorithm can compare before/after
 * list of errors without getting confused by changes in character position.
 * @since 2.4
 */
class WikipediaRuleMatch extends RuleMatch {

  private final String errorContext;
  private final String title;
  private final Date editDate;
  
  WikipediaRuleMatch(RuleMatch ruleMatch, String errorContext, String title, Date editDate) {
    super(ruleMatch.getRule(), ruleMatch.getFromPos(), ruleMatch.getToPos(), ruleMatch.getMessage());
    this.errorContext = Objects.requireNonNull(errorContext);
    this.title = Objects.requireNonNull(title);
    this.editDate = Objects.requireNonNull(editDate);
  }

  String getErrorContext() {
    return errorContext;
  }

  String getTitle() {
    return title;
  }

  Date getEditDate() {
    return editDate;
  }

  @Override
  public String toString() {
    return getRule().getId() + ":" + getFromPos() + "-" + getToPos();
  }

  @Override
  public int hashCode() {
    return getRule().getId().hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof WikipediaRuleMatch) {
      return new EqualsBuilder().append(getRule().getId(), ((WikipediaRuleMatch) other).getRule().getId()).isEquals();
    }
    return false;
  }
}
