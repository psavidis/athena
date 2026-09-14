package com.athena.web.reviewui;

import com.athena.semantic.ModuleDependency;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.ModuleGrouper;
import com.athena.semantic.ModuleTerritory;
import com.athena.semantic.ModuleTopology;
import com.athena.semantic.ModuleTopologyBuilder;
import com.athena.web.ChangeKey;
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
 * out revisions {@link ChangeMapController} already uses.
 */
@RestController
public class ModuleTopologyController {

    private final WebSession session;

    public ModuleTopologyController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/topology")
    public ModuleTopologyResponse topology() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        WebSession.SelectedPullRequest selection = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));

        List<ModuleGroup> changedGroups = new ModuleGrouper().group(selection.changes());
        ModuleTopology topology = new ModuleTopologyBuilder()
                .build(changedGroups, selection.baseRoot(), selection.headRoot());

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
        return new ModuleTerritoryResponse(territory.moduleName(), territory.status(), territory.fileCount(),
                territory.statusSummary(), territory.techStack(), territory.techStack().label(), changeKeys);
    }

    private static ModuleDependencyResponse dependencyResponse(ModuleDependency dependency) {
        return new ModuleDependencyResponse(dependency.from(), dependency.to());
    }
}
