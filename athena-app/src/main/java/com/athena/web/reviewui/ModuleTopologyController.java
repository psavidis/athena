package com.athena.web.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ModuleDependency;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.ModuleGrouper;
import com.athena.semantic.ModuleTerritory;
import com.athena.semantic.ModuleTopology;
import com.athena.semantic.ModuleTopologyBuilder;
import com.athena.web.ChangeKey;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Serves the Semantic Canvas territory map (ticket #129): the module
 * territories a PR touches or references, and the real dependency rails
 * between them, built by {@link ModuleTopologyBuilder} from the same checked-
 * out revisions {@link ChangeMapController} already uses. Works the same for
 * a selected PR Review or a standalone Diff (ticket #111/#153) — this view
 * only ever needs {@link Diff}-shaped data, never PR-specific metadata, so
 * it doesn't require a GitHub connection either.
 */
@RestController
public class ModuleTopologyController {

    private final WebSession session;

    public ModuleTopologyController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/topology")
    public ModuleTopologyResponse topology() {
        Diff diff = requireSelection();

        List<ModuleGroup> changedGroups = new ModuleGrouper().group(diff.changes());
        ModuleTopology topology = new ModuleTopologyBuilder()
                .build(changedGroups, diff.baseRoot(), diff.headRoot());

        List<ModuleTerritoryResponse> territories = topology.territories().stream()
                .map(ModuleTopologyController::territoryResponse)
                .toList();
        List<ModuleDependencyResponse> dependencies = topology.dependencies().stream()
                .map(ModuleTopologyController::dependencyResponse)
                .toList();
        return new ModuleTopologyResponse(territories, dependencies);
    }

    private static ModuleTerritoryResponse territoryResponse(ModuleTerritory territory) {
        List<String> changeKeys = territory.changes().stream().map(ChangeKey::encode).toList();
        List<String> testChangeKeys = territory.changes().stream().filter(Change::isTestCode).map(ChangeKey::encode).toList();
        return new ModuleTerritoryResponse(territory.moduleName(), territory.status(), territory.fileCount(),
                territory.statusSummary(), territory.techStack(), territory.techStack().label(), changeKeys,
                testChangeKeys);
    }

    private static ModuleDependencyResponse dependencyResponse(ModuleDependency dependency) {
        return new ModuleDependencyResponse(dependency.from(), dependency.to());
    }

    /**
     * A standalone Diff needs no GitHub connection at all (ticket #111/#153), so this only
     * ever reports the GitHub-specific 401 when there is truly nothing usable selected AND
     * no token — the same "not connected to GitHub" reviewers saw before this ticket for the
     * PR-only flow. A session with a token but nothing selected still reports 409, unchanged.
     */
    private Diff requireSelection() {
        return session.currentDiff().orElseThrow(() -> {
            if (session.gitHubToken().isEmpty()) {
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub");
            }
            return new ResponseStatusException(HttpStatus.CONFLICT, "No PR or Diff selected");
        });
    }
}
