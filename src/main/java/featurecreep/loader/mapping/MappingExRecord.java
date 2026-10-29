package featurecreep.loader.mapping;

/**
 * One row of a MappingExFormat file. Absent values ({@code nil} or empty) are {@code null}.
 */
public record MappingExRecord(Type type, String original, String newName, String methodName, String position,
        String description) {

    public enum Type {
        CLASS, VAR, DEF, PARAM, INCLUDE, ACCESS_FLAG;

        /**
         * Maps a record-type cell to its type, accepting the English and Spanish spellings.
         * Returns {@code null} for unknown types so newer spec rows can be skipped.
         */
        public static Type parse(String value) {
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "class" -> CLASS;
                case "var" -> VAR;
                case "def" -> DEF;
                case "param" -> PARAM;
                case "include", "incluir" -> INCLUDE;
                case "accessflag", "banderadeacceso" -> ACCESS_FLAG;
                default -> null;
            };
        }
    }
}
