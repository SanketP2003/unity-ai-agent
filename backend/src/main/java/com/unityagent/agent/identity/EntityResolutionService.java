package com.unityagent.agent.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Multi-signal entity resolution and duplicate prevention service.
 *
 * <p>Prevents the autonomous agent from blindly creating duplicate GameObjects,
 * scripts, or assets when identical or compatible entities already exist in the scene.
 *
 * <p>Scoring signals:
 * <ul>
 *   <li><b>Name similarity:</b> Normalized matching with case/affix tolerance</li>
 *   <li><b>Hierarchy path:</b> Location in scene tree</li>
 *   <li><b>Component signature:</b> Overlap of required components</li>
 *   <li><b>Script identity:</b> Presence of target behavior script</li>
 * </ul>
 */
@Service
public class EntityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(EntityResolutionService.class);

    private static final double EXACT_MATCH_THRESHOLD = 0.85;
    private static final double PARTIAL_MATCH_THRESHOLD = 0.50;

    /**
     * Resolves a target entity requirement against known scene candidates.
     *
     * @param target the desired entity specification
     * @param candidates list of existing entities in the Unity scene/project
     * @param allExistingNames set of all currently known names in the scene for disambiguation
     * @return resolution result with recommendation (REUSE, ADAPT, or CREATE_NEW)
     */
    public EntityResolutionResult resolveEntity(EntityCandidate target,
                                               List<EntityCandidate> candidates,
                                               Collection<String> allExistingNames) {
        if (target == null) {
            throw new IllegalArgumentException("Target entity cannot be null");
        }

        if (candidates == null || candidates.isEmpty()) {
            String disambiguated = generateDisambiguatedName(target.getName(), allExistingNames);
            return EntityResolutionResult.noMatch(disambiguated, "No existing scene entities to match against");
        }

        EntityCandidate bestCandidate = null;
        double bestScore = -1.0;
        String bestRationale = "";

        for (EntityCandidate candidate : candidates) {
            double nameScore = computeNameSimilarity(target.getName(), candidate.getName());
            double pathScore = computePathSimilarity(target.getHierarchyPath(), candidate.getHierarchyPath());
            double compScore = computeComponentOverlap(target.getComponents(), candidate.getComponents());
            double scriptScore = computeScriptMatch(target.getScriptName(), candidate.getComponents(), candidate.getScriptName());

            // Weighted combination: Name (30%), Path (20%), Components (35%), Script (15%)
            double compositeScore = (0.30 * nameScore) + (0.20 * pathScore) + (0.35 * compScore) + (0.15 * scriptScore);

            // Boost score if exact name match
            if (target.getName() != null && target.getName().equalsIgnoreCase(candidate.getName())) {
                compositeScore = Math.max(compositeScore, 0.85);
            }

            if (compositeScore > bestScore) {
                bestScore = compositeScore;
                bestCandidate = candidate;
                bestRationale = String.format("Candidate '%s' score %.2f (name=%.2f, comp=%.2f, path=%.2f, script=%.2f)",
                        candidate.getName(), compositeScore, nameScore, compScore, pathScore, scriptScore);
            }
        }

        if (bestCandidate != null && bestScore >= EXACT_MATCH_THRESHOLD) {
            log.info("Resolved EXACT_MATCH for '{}' -> '{}' (score: {:.2f})", target.getName(), bestCandidate.getName(), bestScore);
            return EntityResolutionResult.exactMatch(
                    bestCandidate.getEntityId(),
                    bestCandidate.getName(),
                    bestScore,
                    bestCandidate.getComponents(),
                    bestRationale
            );
        } else if (bestCandidate != null && bestScore >= PARTIAL_MATCH_THRESHOLD) {
            log.info("Resolved PARTIAL_MATCH for '{}' -> '{}' (score: {:.2f})", target.getName(), bestCandidate.getName(), bestScore);
            return EntityResolutionResult.partialMatch(
                    bestCandidate.getEntityId(),
                    bestCandidate.getName(),
                    bestScore,
                    bestCandidate.getComponents(),
                    bestRationale
            );
        } else {
            String disambiguated = generateDisambiguatedName(target.getName(), allExistingNames);
            log.info("Resolved NO_MATCH for '{}' -> Suggest new name '{}'", target.getName(), disambiguated);
            return EntityResolutionResult.noMatch(disambiguated, "No candidate exceeded similarity threshold (best score: " + String.format("%.2f", Math.max(0.0, bestScore)) + ")");
        }
    }

    /**
     * Generates a disambiguated name if the target name already exists in the scene.
     * E.g. "Player" -> "Player_02" if "Player" already exists.
     */
    public String generateDisambiguatedName(String baseName, Collection<String> existingNames) {
        if (baseName == null || baseName.isBlank()) {
            baseName = "GameObject";
        }
        if (existingNames == null || !existingNames.contains(baseName)) {
            return baseName;
        }

        int index = 2;
        while (true) {
            String candidate = String.format("%s_%02d", baseName, index);
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private double computeNameSimilarity(String name1, String name2) {
        if (name1 == null || name2 == null) return 0.0;
        String s1 = normalizeName(name1);
        String s2 = normalizeName(name2);
        if (s1.equals(s2)) return 1.0;
        if (s1.contains(s2) || s2.contains(s1)) return 0.8;
        return 0.0;
    }

    private double computePathSimilarity(String path1, String path2) {
        if (path1 == null || path2 == null) return 0.5; // neutral if not specified
        if (path1.equalsIgnoreCase(path2)) return 1.0;
        if (path1.endsWith(path2) || path2.endsWith(path1)) return 0.75;
        return 0.0;
    }

    private double computeComponentOverlap(List<String> targetComponents, List<String> candidateComponents) {
        if (targetComponents == null || targetComponents.isEmpty()) return 0.5;
        if (candidateComponents == null || candidateComponents.isEmpty()) return 0.0;

        Set<String> targetSet = targetComponents.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> candidateSet = candidateComponents.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long matches = targetSet.stream().filter(candidateSet::contains).count();
        return (double) matches / (double) targetSet.size();
    }

    private double computeScriptMatch(String targetScript, List<String> components, String candidateScript) {
        if (targetScript == null || targetScript.isBlank()) return 0.5; // neutral
        if (candidateScript != null && candidateScript.equalsIgnoreCase(targetScript)) return 1.0;
        if (components != null) {
            for (String comp : components) {
                if (comp.equalsIgnoreCase(targetScript)) return 1.0;
            }
        }
        return 0.0;
    }

    private String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
