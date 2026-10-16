package com.galiehneh.wordcraft.res;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;

/** Every translatable string exists in both the default (EN) and Indonesian locale. */
public class StringsParityTest {
    private static Set<String> keys(String path) throws Exception {
        NodeList list = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File(path)).getElementsByTagName("string");
        Set<String> keys = new TreeSet<>();
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            if (!"false".equals(e.getAttribute("translatable"))) keys.add(e.getAttribute("name"));
        }
        return keys;
    }

    @Test
    public void bothLocalesDefineTheSameKeys() throws Exception {
        Set<String> en = keys("src/main/res/values/strings.xml");
        Set<String> in = keys("src/main/res/values-in/strings.xml");
        assertFalse(en.isEmpty());
        assertEquals(en, in);
    }
}
