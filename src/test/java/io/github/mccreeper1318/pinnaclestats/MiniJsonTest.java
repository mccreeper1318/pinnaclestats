package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniJsonTest {
    @Test
    void acceptsValidVanillaStatisticsJson() {
        String json = """
                {
                  "stats": {
                    "minecraft:custom": {
                      "minecraft:play_time": 2400,
                      "minecraft:deaths": 3
                    },
                    "minecraft:mined": {
                      "minecraft:stone": 42
                    }
                  },
                  "DataVersion": 4440
                }
                """;

        Object parsed = MiniJson.parse(json);

        assertTrue(parsed instanceof Map<?, ?>);
        Map<?, ?> root = (Map<?, ?>) parsed;
        assertEquals(4440L, root.get("DataVersion"));
        Map<?, ?> stats = (Map<?, ?>) root.get("stats");
        Map<?, ?> custom = (Map<?, ?>) stats.get("minecraft:custom");
        assertEquals(2400L, custom.get("minecraft:play_time"));
    }

    @Test
    void rejectsTruncatedJson() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MiniJson.parse("{\"stats\":{\"minecraft:custom\":")
        );

        assertTrue(ex.getMessage().contains("Unexpected end"));
    }

    @Test
    void rejectsTrailingNonWhitespaceContent() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MiniJson.parse("{\"stats\":{}} trailing")
        );

        assertTrue(ex.getMessage().contains("Trailing content"));
    }

    @Test
    void acceptsTrailingWhitespace() {
        Object parsed = MiniJson.parse("{\"stats\":{}} \n\r\t ");
        assertTrue(parsed instanceof Map<?, ?>);
    }

    @Test
    void rejectsInputOverUtf8ByteLimit() {
        String json = "\"é\"";

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MiniJson.parse(json, 3, 8)
        );

        assertTrue(ex.getMessage().contains("maximum size of 3 bytes"));
        assertTrue(ex.getMessage().contains("received 4"));
    }

    @Test
    void rejectsExcessiveNestingBeforeStackOverflow() {
        String nested = "0";
        for (int i = 0; i < 6; i++) {
            nested = "[" + nested + "]";
        }
        String input = nested;

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MiniJson.parse(input, 1024, 5)
        );

        assertTrue(ex.getMessage().contains("maximum depth of 5"));
    }

    @Test
    void rejectsInvalidJsonNumbers() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("1."));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("1e"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("-"));
    }
}
