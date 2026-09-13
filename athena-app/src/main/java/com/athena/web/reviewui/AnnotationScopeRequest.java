package com.athena.web.reviewui;

import com.athena.reviewui.AnnotationScope;
import com.athena.semantic.Change;
import com.athena.web.ChangeKey;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * The wire shape of an {@link AnnotationScope} (ticket #75) — a discriminated
 * union over the same four kinds, since a request body can't carry a Java
 * value type directly. Only the fields relevant to {@code type} are set.
 */
public record AnnotationScopeRequest(ScopeType type, String filePath, Integer line, String symbolDescription,
                                      String changeKey) {

    public enum ScopeType { LINE, SYMBOL, CHANGE, REVIEW }

    /** Resolves to a real {@link AnnotationScope}, looking up the Change for {@code CHANGE} scope among {@code changes}. */
    public AnnotationScope toScope(List<Change> changes) {
        return switch (type) {
            case LINE -> AnnotationScope.line(filePath, line);
            case SYMBOL -> AnnotationScope.symbol(symbolDescription);
            case CHANGE -> AnnotationScope.change(resolveChange(changes));
            case REVIEW -> AnnotationScope.review();
        };
    }

    private Change resolveChange(List<Change> changes) {
        Optional<Change> change = ChangeKey.resolve(changeKey, changes);
        return change.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }
}
