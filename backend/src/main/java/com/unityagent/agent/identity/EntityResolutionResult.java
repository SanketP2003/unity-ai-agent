package com.unityagent.agent.identity;

import java.util.List;

/**
 * Structured outcome of resolving a target entity against existing scene objects and assets.
 */
public class EntityResolutionResult {

    public enum MatchType {
        EXACT_MATCH,    // Confidence >= 0.85 -> Reuse existing object, do NOT recreate
        PARTIAL_MATCH,  // Confidence 0.50..0.84 -> Inspect and adapt/upgrade
        NO_MATCH        // Confidence < 0.50 -> Safe to create new entity
    }

    private final MatchType matchType;
    private final double confidence;
    private final String resolvedEntityId;
    private final String matchedName;
    private final String disambiguatedName;
    private final List<String> matchedComponents;
    private final String rationale;

    public EntityResolutionResult(MatchType matchType, double confidence, String resolvedEntityId,
                                  String matchedName, String disambiguatedName,
                                  List<String> matchedComponents, String rationale) {
        this.matchType = matchType;
        this.confidence = confidence;
        this.resolvedEntityId = resolvedEntityId;
        this.matchedName = matchedName;
        this.disambiguatedName = disambiguatedName;
        this.matchedComponents = matchedComponents != null ? List.copyOf(matchedComponents) : List.of();
        this.rationale = rationale;
    }

    public static EntityResolutionResult exactMatch(String entityId, String name, double confidence,
                                                    List<String> components, String rationale) {
        return new EntityResolutionResult(MatchType.EXACT_MATCH, confidence, entityId, name, name, components, rationale);
    }

    public static EntityResolutionResult partialMatch(String entityId, String name, double confidence,
                                                      List<String> components, String rationale) {
        return new EntityResolutionResult(MatchType.PARTIAL_MATCH, confidence, entityId, name, name, components, rationale);
    }

    public static EntityResolutionResult noMatch(String disambiguatedName, String rationale) {
        return new EntityResolutionResult(MatchType.NO_MATCH, 0.0, null, null, disambiguatedName, List.of(), rationale);
    }

    public MatchType getMatchType() { return matchType; }
    public double getConfidence() { return confidence; }
    public String getResolvedEntityId() { return resolvedEntityId; }
    public String getMatchedName() { return matchedName; }
    public String getDisambiguatedName() { return disambiguatedName; }
    public List<String> getMatchedComponents() { return matchedComponents; }
    public String getRationale() { return rationale; }

    public boolean shouldReuse() {
        return matchType == MatchType.EXACT_MATCH;
    }

    public boolean shouldAdapt() {
        return matchType == MatchType.PARTIAL_MATCH;
    }

    public boolean shouldCreateNew() {
        return matchType == MatchType.NO_MATCH;
    }

    @Override
    public String toString() {
        return "EntityResolutionResult{" +
                "type=" + matchType +
                ", confidence=" + String.format("%.2f", confidence) +
                ", id='" + resolvedEntityId + '\'' +
                ", name='" + matchedName + '\'' +
                ", disambiguated='" + disambiguatedName + '\'' +
                '}';
    }
}
