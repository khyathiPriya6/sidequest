package com.sidequest.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Conservative, explainable triage hints; NOT a complete content moderation classifier.
 * No automatic approval or punitive rejection from keyword matching alone.
 */
@Component
public class QuestSafetyScreen {
    public enum Risk { NO_SIGNAL, REVIEW, HIGH_PRIORITY_REVIEW }
    public enum Reason { PHYSICAL_DANGER, PRIVACY, FRAUD, UNCLEAR_SAFETY }
    public record Result(Risk risk, List<Reason> reasons) {
        public Result { reasons = List.copyOf(reasons); }
    }

    private static final Pattern DANGEROUS_DRIVING = Pattern.compile(
            "\\b(?:speed|race|drive|driving)\\b.{0,80}\\b(?:traffic|public road|highway|speed limit)\\b"
            + "|\\b(?:traffic|public road|highway|speed limit)\\b.{0,80}\\b(?:speed|race|drive|driving)\\b");
    private static final Pattern PRIVATE_INFORMATION = Pattern.compile(
            "\\b(?:publish|share|post|expose|leak|find)\\b.{0,100}\\b"
            + "(?:private address|home address|passwords?|otp|one.time password|someone.s phone number)\\b");
    private static final Pattern FRAUD = Pattern.compile(
            "\\b(?:steal|collect|harvest)\\b.{0,100}\\b(?:passwords?|login credentials|credit card numbers?)\\b");
    private static final Pattern AMBIGUOUS_RISK = Pattern.compile(
            "\\b(?:climb|jump|break into|trespass|dangerous|challenge strangers)\\b");

    public Result assess(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<Reason> reasons = new ArrayList<>();
        if (DANGEROUS_DRIVING.matcher(text).find()) reasons.add(Reason.PHYSICAL_DANGER);
        if (PRIVATE_INFORMATION.matcher(text).find()) reasons.add(Reason.PRIVACY);
        if (FRAUD.matcher(text).find()) reasons.add(Reason.FRAUD);
        if (!reasons.isEmpty()) return new Result(Risk.HIGH_PRIORITY_REVIEW, reasons);
        if (AMBIGUOUS_RISK.matcher(text).find()) {
            return new Result(Risk.REVIEW, List.of(Reason.UNCLEAR_SAFETY));
        }
        return new Result(Risk.NO_SIGNAL, List.of());
    }
}
