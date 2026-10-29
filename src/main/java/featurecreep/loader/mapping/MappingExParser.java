package featurecreep.loader.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads MappingExFormat ({@code .pdme}) text. The first line is a header and is ignored.
 * Fields are separated by {@code ¶}; each following line is one record.
 */
public final class MappingExParser {

    public static final char DELIMITER = '¶';

    private static final String NIL = "nil";

    private MappingExParser() {
    }

    public static List<MappingExRecord> parse(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static List<MappingExRecord> parse(Reader reader) throws IOException {
        BufferedReader in = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);
        List<MappingExRecord> records = new ArrayList<>();
        boolean header = true;
        int lineNumber = 0;
        String line;
        while ((line = in.readLine()) != null) {
            lineNumber++;
            if (header) {
                header = false;
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(String.valueOf(DELIMITER), -1);
            MappingExRecord.Type type = MappingExRecord.Type.parse(fields[0].trim());
            if (type == null) {
                continue;
            }
            String newName = field(fields, 2);
            if (type == MappingExRecord.Type.ACCESS_FLAG) {
                if (newName == null) {
                    throw new IOException("MappingEx line " + lineNumber + ": AccessFlag requires a flag value");
                }
                try {
                    parseFlags(newName);
                } catch (IllegalArgumentException e) {
                    throw new IOException("MappingEx line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
            records.add(new MappingExRecord(type, field(fields, 1), newName, field(fields, 3), field(fields, 4),
                    field(fields, 5)));
        }
        return List.copyOf(records);
    }

    /**
     * Parses an access flag value written as decimal ({@code 1}) or hexadecimal ({@code 0x0001}).
     * Leading zeros in decimal values are not octal.
     */
    public static int parseFlags(String value) {
        String text = value.trim();
        int flags;
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                flags = Integer.parseInt(text.substring(2), 16);
            } else {
                flags = Integer.parseInt(text);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid access flag '" + value + "'", e);
        }
        if (flags < 0 || flags > 0xFFFF) {
            throw new IllegalArgumentException("access flag out of range '" + value + "'");
        }
        return flags;
    }

    private static String field(String[] fields, int index) {
        if (index >= fields.length) {
            return null;
        }
        String value = fields[index].trim();
        return value.isEmpty() || NIL.equals(value) ? null : value;
    }
}
