package com.athena.web.reviewui;

import com.athena.reviewui.ChangeMapEntry;
import com.athena.reviewui.ChangeMapView;
import com.athena.reviewui.ClassGroup;
import com.athena.reviewui.PrUnderstandingView;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the Change Map + PR Understanding View for the selected PR's
 * checked-out revisions (ticket #74), reusing {@link ChangeMapView} and
 * {@link PrUnderstandingView} unmodified. Semantic detection itself is run
 * once per selection and cached on {@link WebSession.SelectedPullRequest},
 * not repeated here.
 */
@RestController
public class ChangeMapController {

    private final WebSession session;

    public ChangeMapController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/change-map")
    public ChangeMapResponse changeMap() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        WebSession.SelectedPullRequest selection = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));

        List<Change> changes = selection.changes();

        ChangeMapView changeMapView = ChangeMapView.of(changes, selection.reviewStateStore());
        PrUnderstandingView understandingView =
                PrUnderstandingView.of(selection.pullRequest().title(), changes);

        Map<ChangeMapEntry, ChangeEntryResponse> byEntry = entryResponsesByEntry(changeMapView);
        List<ChangeEntryResponse> flatEntries = changeMapView.entries().stream().map(byEntry::get).toList();
        List<ClassGroupResponse> classGroups = classGroups(changeMapView, byEntry);

        return new ChangeMapResponse(understandingView.prTitle(), categoryCounts(understandingView), flatEntries, classGroups);
    }

    private static Map<ChangeCategory, Integer> categoryCounts(PrUnderstandingView understandingView) {
        Map<ChangeCategory, Integer> counts = new LinkedHashMap<>();
        for (ChangeCategory category : ChangeCategory.values()) {
            counts.put(category, understandingView.countFor(category));
        }
        return counts;
    }

    /**
     * Builds each entry's response, keyed by the source {@link ChangeMapEntry}
     * (identity-safe — two distinct entries are never the same object) so
     * both the flat list and {@link #classGroups} can reuse the same response
     * objects instead of re-deriving them. The map's own iteration order is
     * irrelevant — callers re-derive order from {@link ChangeMapView} itself.
     */
    private static Map<ChangeMapEntry, ChangeEntryResponse> entryResponsesByEntry(ChangeMapView changeMapView) {
        List<ChangeMapEntry> viewEntries = changeMapView.entries();
        Map<ChangeMapEntry, ChangeEntryResponse> byEntry = new IdentityHashMap<>(viewEntries.size());
        for (int i = 0; i < viewEntries.size(); i++) {
            ChangeMapEntry entry = viewEntries.get(i);
            byEntry.put(entry, new ChangeEntryResponse(i, ChangeKey.encode(entry.change()), entry.description(),
                    entry.category(), entry.change().kind(), entry.reviewState(), entry.change().occurrenceCount(),
                    entry.change().exceptionCount(), entry.change().isTestCode()));
        }
        return byEntry;
    }

    private static List<ClassGroupResponse> classGroups(ChangeMapView changeMapView,
                                                          Map<ChangeMapEntry, ChangeEntryResponse> byEntry) {
        List<ClassGroupResponse> groups = new ArrayList<>();
        for (ClassGroup group : changeMapView.classGroups()) {
            List<ChangeEntryResponse> entries = group.entries().stream().map(byEntry::get).toList();
            groups.add(new ClassGroupResponse(group.enclosingType(), entries));
        }
        return groups;
    }
}
