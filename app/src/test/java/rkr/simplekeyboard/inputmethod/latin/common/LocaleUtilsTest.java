package rkr.simplekeyboard.inputmethod.latin.common;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class LocaleUtilsTest {

    @Test
    public void testConstructLocaleFromString() {
        Locale es = LocaleUtils.constructLocaleFromString("es");
        assertEquals("es", es.getLanguage());

        Locale esUS = LocaleUtils.constructLocaleFromString("es_US");
        assertEquals("es", esUS.getLanguage());
        assertEquals("US", esUS.getCountry());

        Locale enUSVar = LocaleUtils.constructLocaleFromString("en_US_POSIX");
        assertEquals("en", enUSVar.getLanguage());
        assertEquals("US", enUSVar.getCountry());
        assertEquals("POSIX", enUSVar.getVariant());
    }

    @Test
    public void testGetLocaleString() {
        assertEquals("es", LocaleUtils.getLocaleString(new Locale("es")));
        assertEquals("es_AR", LocaleUtils.getLocaleString(new Locale("es", "AR")));
        assertEquals("en_US_POSIX", LocaleUtils.getLocaleString(new Locale("en", "US", "POSIX")));
    }

    @Test
    public void testFindBestLocaleExact() {
        Locale target = new Locale("es", "AR");
        List<Locale> options = Arrays.asList(new Locale("en", "US"), new Locale("es", "ES"), new Locale("es", "AR"));

        Locale match = LocaleUtils.findBestLocale(target, options);
        assertEquals(target, match);
    }

    @Test
    public void testFindBestLocaleLanguageFallback() {
        Locale target = new Locale("es", "MX");
        List<Locale> options = Arrays.asList(new Locale("en", "US"), new Locale("es", "ES"));

        Locale match = LocaleUtils.findBestLocale(target, options);
        assertEquals(new Locale("es", "ES"), match);
    }

    @Test
    public void testFindBestLocaleNoMatch() {
        Locale target = new Locale("fr", "FR");
        List<Locale> options = Arrays.asList(new Locale("en", "US"), new Locale("es", "ES"));

        assertNull(LocaleUtils.findBestLocale(target, options));
    }
}
