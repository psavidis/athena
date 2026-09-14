package com.athena.semantic;

import java.util.List;

/**
 * The full territory map for a PR (ticket #129): every {@link ModuleTerritory}
 * the canvas should draw a region for, and the {@link ModuleDependency} edges
 * to draw rails between them. Built by {@link ModuleTopologyBuilder}.
 */
public final class ModuleTopology {

    private final List<ModuleTerritory> territories;
    private final List<ModuleDependency> dependencies;

    ModuleTopology(List<ModuleTerritory> territories, List<ModuleDependency> dependencies) {
        this.territories = List.copyOf(territories);
        this.dependencies = List.copyOf(dependencies);
    }

    public List<ModuleTerritory> territories() {
        return territories;
    }

    public List<ModuleDependency> dependencies() {
        return dependencies;
    }
}
