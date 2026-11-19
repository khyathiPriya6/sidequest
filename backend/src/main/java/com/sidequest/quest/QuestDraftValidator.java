package com.sidequest.quest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

final class QuestDraftValidator {
    private QuestDraftValidator() { }

    static DraftValues create(QuestDtos.CreateDraft request) {
        if (request == null) {
            throw QuestApiFailure.badRequest("malformed_request", "Provide a JSON object for the quest.");
        }
        var errors = new ArrayList<QuestApiFailure.FieldError>();
        String title = text(request.title(), "title", false, errors);
        String description = text(request.description(), "description", true, errors);
        QuestCategory category = category(request.category(), errors);
        QuestDifficulty difficulty = difficulty(request.difficulty(), errors);
        Integer rewardXp = reward(request.rewardXp(), errors);
        validateStrings(title, description, errors);
        failIfInvalid(errors);
        return new DraftValues(title, description, category, difficulty, rewardXp);
    }

    static DraftValues patch(Quest quest, QuestDtos.PatchDraft request) {
        if (request == null) {
            throw QuestApiFailure.badRequest("malformed_request", "Provide a JSON object for the patch.");
        }
        var errors = new ArrayList<QuestApiFailure.FieldError>();
        String title = request.title() == null
                ? quest.getTitle() : text(request.title(), "title", false, errors);
        String description = request.description() == null
                ? quest.getDescription() : text(request.description(), "description", true, errors);
        QuestCategory category = request.category() == null
                ? quest.getCategory() : category(request.category(), errors);
        QuestDifficulty difficulty = request.difficulty() == null
                ? quest.getDifficulty() : difficulty(request.difficulty(), errors);
        Integer rewardXp = request.rewardXp() == null
                ? quest.getRewardXp() : reward(request.rewardXp(), errors);
        validateStrings(title, description, errors);
        failIfInvalid(errors);
        return new DraftValues(title, description, category, difficulty, rewardXp);
    }

    static List<QuestApiFailure.FieldError> publicationErrors(Quest quest) {
        var errors = new ArrayList<QuestApiFailure.FieldError>();
        if (quest.getDescription() == null) {
            errors.add(error("description", "required", "Description is required when publishing."));
        }
        if (quest.getCategory() == null) {
            errors.add(error("category", "required", "Category is required when publishing."));
        }
        if (quest.getDifficulty() == null) {
            errors.add(error("difficulty", "required", "Difficulty is required when publishing."));
        }
        if (quest.getRewardXp() == null) {
            errors.add(error("rewardXp", "required", "XP reward is required when publishing."));
        }
        return List.copyOf(errors);
    }

    private static String text(JsonNode node, String field, boolean nullable,
            List<QuestApiFailure.FieldError> errors) {
        if (node == null || node.isNull()) {
            if (!nullable) {
                errors.add(error(field, "required", "Title is required."));
            }
            return null;
        }
        if (!node.isTextual()) {
            errors.add(error(field, "invalid_type", "Provide a string value."));
            return null;
        }
        return node.textValue().strip();
    }

    private static QuestCategory category(JsonNode node, List<QuestApiFailure.FieldError> errors) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            errors.add(error("category", "invalid_type", "Provide a category name."));
            return null;
        }
        try {
            return QuestCategory.valueOf(node.textValue());
        } catch (IllegalArgumentException exception) {
            errors.add(error("category", "invalid_enum", "Choose a supported quest category."));
            return null;
        }
    }

    private static QuestDifficulty difficulty(JsonNode node, List<QuestApiFailure.FieldError> errors) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            errors.add(error("difficulty", "invalid_type", "Provide a difficulty name."));
            return null;
        }
        try {
            return QuestDifficulty.valueOf(node.textValue());
        } catch (IllegalArgumentException exception) {
            errors.add(error("difficulty", "invalid_enum", "Choose EASY, MEDIUM, or HARD."));
            return null;
        }
    }

    private static Integer reward(JsonNode node, List<QuestApiFailure.FieldError> errors) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            errors.add(error("rewardXp", "invalid_type", "Provide an integer XP reward."));
            return null;
        }
        int value = node.intValue();
        if (value < 1 || value > 1000) {
            errors.add(error("rewardXp", "range", "XP reward must be between 1 and 1000."));
            return null;
        }
        return value;
    }

    private static void validateStrings(String title, String description,
            List<QuestApiFailure.FieldError> errors) {
        if (title != null) {
            int length = title.codePointCount(0, title.length());
            if (title.isBlank()) {
                errors.add(error("title", "required", "Title is required."));
            } else if (length < 3 || length > 120) {
                errors.add(error("title", "length", "Title must contain 3 to 120 Unicode code points."));
            }
        }
        if (description != null) {
            int length = description.codePointCount(0, description.length());
            if (length < 10 || length > 3000) {
                errors.add(error("description", "length",
                        "Description must contain 10 to 3000 Unicode code points."));
            }
        }
    }

    private static QuestApiFailure.FieldError error(String field, String code, String message) {
        return new QuestApiFailure.FieldError(field, code, message);
    }

    private static void failIfInvalid(List<QuestApiFailure.FieldError> errors) {
        if (!errors.isEmpty()) throw QuestApiFailure.validation(errors);
    }

    record DraftValues(String title, String description, QuestCategory category,
            QuestDifficulty difficulty, Integer rewardXp) { }
}
