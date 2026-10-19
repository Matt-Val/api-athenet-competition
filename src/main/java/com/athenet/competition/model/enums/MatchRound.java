package com.athenet.competition.model.enums;

/*
    Rondas de la copa, en orden. El ganador del partido N de una ronda pasa al
    partido ⌈N/2⌉ de la siguiente.
*/
public enum MatchRound {
    ROUND_OF_16("Octavos de final", 8),
    QUARTER_FINAL("Cuartos de final", 4),
    SEMI_FINAL("Semifinal", 2),
    FINAL("Final", 1);

    private final String label;
    private final int matches;

    MatchRound(String label, int matches) {
        this.label = label;
        this.matches = matches;
    }

    public String getLabel() {
        return label;
    }

    public int getMatches() {
        return matches;
    }

    // null después de la final
    public MatchRound next() {
        return this == FINAL ? null : values()[ordinal() + 1];
    }
}
