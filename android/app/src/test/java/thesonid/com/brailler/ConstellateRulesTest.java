package thesonid.com.brailler;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConstellateRulesTest {
  @Test
  public void wordLimitsMatchTheEightCellBoard() {
    assertTrue(ConstellateRules.validWord(" Engineer "));
    assertTrue(ConstellateRules.validWord("a"));
    for (String invalid : new String[] {"", "abcdefghi", "two words", "123", "café", "你", "a/b"})
      assertFalse(invalid, ConstellateRules.validWord(invalid));
  }

  @Test
  public void invalidFirebasePathsCannotBecomeBoardIdentifiersOrPasswordKeys() {
    assertTrue(ConstellateRules.validDatabaseKey("board-01"));
    for (String invalid :
        new String[] {"", "/", "a.b", "x#", "$x", "[x]", "a\nb", "a\u0000b", "a\u007fb"})
      assertFalse(invalid, ConstellateRules.validDatabaseKey(invalid));
    assertFalse(ConstellateRules.validDatabaseKey(null));
  }

  @Test
  public void practiceIgnoresCaseAndOuterWhitespaceButNotMisspellings() {
    assertTrue(ConstellateRules.correctAnswer(" CAT ", "cat"));
    assertFalse(ConstellateRules.correctAnswer("cart", "cat"));
  }

  @Test
  public void brailleUsesStandardSixDotOrderIncludingW() {
    assertTrue(BraillePreview.isRaised('a', 1));
    assertFalse(BraillePreview.isRaised('a', 4));
    assertTrue(BraillePreview.isRaised('w', 2));
    assertTrue(BraillePreview.isRaised('w', 4));
    assertTrue(BraillePreview.isRaised('w', 5));
    assertTrue(BraillePreview.isRaised('w', 6));
    assertFalse(BraillePreview.isRaised('w', 1));
    assertFalse(BraillePreview.isRaised('w', 3));
    assertFalse(BraillePreview.isRaised('?', 1));
  }
}
