package com.athena.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal line-based unified diff between two text blocks, computed via
 * longest-common-subsequence so a reviewer sees exactly which lines changed
 * instead of two full-text dumps (epic #4 §10/§44: the underlying diff must
 * always be preservable, regardless of classification).
 */
final class UnifiedDiff {

    private UnifiedDiff() {
    }

    /** Empty when the two texts are identical (nothing to show). */
    static String of(String beforeText, String afterText) {
        if (beforeText.equals(afterText)) {
            return "";
        }
        List<String> before = List.of(beforeText.split("\n", -1));
        List<String> after = List.of(afterText.split("\n", -1));
        List<int[]> lcs = longestCommonSubsequence(before, after);

        StringBuilder diff = new StringBuilder();
        int bi = 0, ai = 0;
        for (int[] pair : lcs) {
            while (bi < pair[0]) {
                diff.append("-").append(before.get(bi)).append('\n');
                bi++;
            }
            while (ai < pair[1]) {
                diff.append("+").append(after.get(ai)).append('\n');
                ai++;
            }
            diff.append(" ").append(before.get(bi)).append('\n');
            bi++;
            ai++;
        }
        while (bi < before.size()) {
            diff.append("-").append(before.get(bi)).append('\n');
            bi++;
        }
        while (ai < after.size()) {
            diff.append("+").append(after.get(ai)).append('\n');
            ai++;
        }
        return diff.toString().stripTrailing();
    }

    /** Matched (base-index, head-index) pairs of equal lines, in order, via classic LCS DP. */
    private static List<int[]> longestCommonSubsequence(List<String> before, List<String> after) {
        int m = before.size();
        int n = after.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                dp[i][j] = before.get(i).equals(after.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<int[]> matches = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m && j < n) {
            if (before.get(i).equals(after.get(j))) {
                matches.add(new int[] {i, j});
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return matches;
    }
}
