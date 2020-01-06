/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.device.activitymanager;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ConfigQualifierParser;
import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ResourceConfigHandler;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for locale qualifiers in the resource config string.
 *
 * <p>Parsing locales is trickier because the qualifier separator '-' is reused as the language
 * region separator, so we sometimes have to advance the iterator here. At the same time, we have to
 * roll back the iterator progress if the parsing fails.
 */
class LocaleParser<T> implements ConfigQualifierParser<T> {

  // "car" is a UiModeType value. It's not a valid language even though it matches the pattern.
  private static final String LANGUAGE_PATTERN = "(?!car)[A-Za-z]{2,3}";
  private static final String BCP47_PREFIX = "b+";
  // A resource qualifier might start with region continuation of the previous language qualifier
  // (e.g. en-rGB) and optionally followed by more locale qualifiers (e.g. en-rGB,fr-rFR).
  private static final String REGION_CONTINUATION_PATTERN = "r[A-Z]{2}($|,.+)";

  private static final Pattern LANGUAGE_REGION_PATTERN =
      Pattern.compile("(?<language>[A-Za-z]{2,3})(-r(?<region>[A-Za-z]{2}))?");
  private static final Splitter BCP47_SPLITTER = Splitter.on('+');
  private static final Splitter COMMA_SPLITTER = Splitter.on(',');
  private static final Joiner HYPHEN_JOINER = Joiner.on('-');
  private static final Joiner PLUS_JOINER = Joiner.on('+');

  /** Parses all locale qualifiers. */
  @Override
  public boolean parse(ConfigStringIterator iterator, ResourceConfigHandler<T> handler) {
    iterator.savePosition();
    try {
      parseInternal(iterator.getValue(), handler, iterator);
      return true;
    } catch (ParseException parseException) {
      iterator.restorePosition();
      return false;
    }
  }

  /**
   * Based on LocaleValue::InitFromParts from Aapt2. Added support for comma separated locales.
   *
   * @throws ParseException if any locale resource qualifier is invalid. The standard semantic is to
   *     rollback the iterator and continue parsing using the next parser in the list. See {@link
   *     ResourceConfigParser}.
   */
  private void parseInternal(
      String nextPart, ResourceConfigHandler<T> handler, Iterator<String> iterator)
      throws ParseException {

    // In order to support comma separated locales and parse them nicely, we try to concatenate
    // all locales (they can use the resource config string separator "-"). And then parse them
    // separately.
    Iterable<String> locales = COMMA_SPLITTER.split(getAllLocaleSegments(nextPart, iterator));

    String language;
    String region;

    for (String locale : locales) {
      if (locale.startsWith(BCP47_PREFIX)) {
        initFromBcp47Tag(locale.substring(BCP47_PREFIX.length()), handler);
      } else {
        Matcher languageRegionMatcher = LANGUAGE_REGION_PATTERN.matcher(locale);
        if (!languageRegionMatcher.matches()) {
          throw new ParseException(
              String.format("Unexpected language/region locale string '%s'.", locale));
        } else {
          language = languageRegionMatcher.group("language");
          region = languageRegionMatcher.group("region");
          checkNotNull(language);
          if (region != null) {
            handler.onLocale(language + "-" + region);
          } else {
            handler.onLocale(language);
          }
        }
      }
    }
  }

  /** Moves the iterator when necessary to read all locales encoded in the config string. */
  private String getAllLocaleSegments(String nextPart, Iterator<String> iterator) {
    ImmutableList.Builder<String> localeParts = ImmutableList.builder();

    // The first part can be just a language (not 'car') or contain ',' or BCP-47 expression.
    if (nextPart.contains(",")
        || nextPart.matches(LANGUAGE_PATTERN)
        || nextPart.startsWith(BCP47_PREFIX)) {
      localeParts.add(nextPart);
      // The next part must contain ',' or be just a region. We will validate against region
      // regex properly when we get to parse the specific locale part.
      while (iterator.hasNext()) {
        nextPart = iterator.next();
        if (nextPart.matches(REGION_CONTINUATION_PATTERN) || nextPart.contains(",")) {
          localeParts.add(nextPart);
        }
      }
    }
    return HYPHEN_JOINER.join(localeParts.build());
  }

  /**
   * Parses BCP-47 tag delimited by '+'.
   *
   * <p>Doesn't support scripts or variants, they are ignored.
   */
  private void initFromBcp47Tag(String bcp47Tag, ResourceConfigHandler<T> handler)
      throws ParseException {
    ImmutableList<String> subtags = ImmutableList.copyOf(BCP47_SPLITTER.split(bcp47Tag));

    String language = subtags.get(0);
    Optional<String> region = extractRegion(subtags);

    if (region.isPresent()) {
      handler.onLocale(language + "-" + region.get());
    } else {
      handler.onLocale(language);
    }
  }

  /** Implementation based on LocaleValue::InitFromBcp47TagImpl from Aapt2. */
  private Optional<String> extractRegion(ImmutableList<String> bcp47SubTags) throws ParseException {
    if (bcp47SubTags.size() == 1) {
      return Optional.empty();
    } else if (bcp47SubTags.size() == 2) {
      String secondSubTag = bcp47SubTags.get(1);
      switch (secondSubTag.length()) {
        case 2:
        case 3:
          return Optional.of(secondSubTag);
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
          // It's variant or script. Bundle Tool doesn't support it.
          return Optional.empty();
        default:
          break;
      }
    } else if (bcp47SubTags.size() == 3) {
      // The second subtag can be a script or region. If it's size 2 or 3 it's region.
      String secondSubTag = bcp47SubTags.get(1);
      if (secondSubTag.length() == 2 || secondSubTag.length() == 3) {
        return Optional.of(secondSubTag);
      }
      String thirdSubTag = bcp47SubTags.get(2);
      if (thirdSubTag.length() == 2 || thirdSubTag.length() == 3) {
        return Optional.of(thirdSubTag);
      } else if (thirdSubTag.length() >= 4) {
        // It's variant.
        return Optional.empty();
      }
    } else if (bcp47SubTags.size() == 4) {
      // If we have all 4 subtags they represent: language-script-region-variant.
      return Optional.of(bcp47SubTags.get(2));
    }

    // Unsupported BCP-47 string if we reach here.

    throw new ParseException(
        String.format(
            "Encountered unsupported or invalid BCP-47 string '%s'.",
            PLUS_JOINER.join(bcp47SubTags)));
  }

  private static class ParseException extends Exception {
    public ParseException(String cause) {
      super(cause);
    }
  }
}
