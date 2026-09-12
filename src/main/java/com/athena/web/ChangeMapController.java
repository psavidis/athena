package com.athena.web;

import com.athena.reviewui.ChangeMapEntry;
import com.athena.reviewui.ChangeMapView;
import com.athena.reviewui.PrUnderstandingView;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs semantic detection against the selected PR's checked-out revisions
 * and serves the Change Map + PR Understanding View (ticket #74). Reuses
 * {@link TransformationDetector}, {@link ChangeGrouper}, {@link ChangeMapView}
 * and {@link PrUnderstandingView} unmodified.
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

        List<DetectedTransformation> transformations =
                new TransformationDetector().detect(selection.baseRoot(), selection.headRoot());
        List<Change> changes = new ChangeGrouper().group(transformations);

        ChangeMapView changeMapView = ChangeMapView.of(changes, selection.reviewStateStore());
        PrUnderstandingView understandingView =
                PrUnderstandingView.of(selection.pullRequest().title(), changes);

        return new ChangeMapResponse(understandingView.prTitle(), categoryCounts(understandingView), entries(changeMapView));
    }

    private static Map<ChangeCategory, Integer> categoryCounts(PrUnderstandingView understandingView) {
        Map<ChangeCategory, Integer> counts = new LinkedHashMap<>();
        for (ChangeCategory category : ChangeCategory.values()) {
            counts.put(category, understandingView.countFor(category));
        }
        return counts;
    }

    private static List<ChangeEntryResponse> entries(ChangeMapView changeMapView) {
        List<ChangeMapEntry> viewEntries = changeMapView.entries();
        List<ChangeEntryResponse> entries = new ArrayList<>(viewEntries.size());
        for (int i = 0; i < viewEntries.size(); i++) {
            ChangeMapEntry entry = viewEntries.get(i);
            entries.add(new ChangeEntryResponse(i, ChangeKey.encode(entry.change()), entry.description(),
                    entry.category(), entry.reviewState(), entry.change().occurrenceCount(),
                    entry.change().exceptionCount()));
        }
        return entries;
    }
}
