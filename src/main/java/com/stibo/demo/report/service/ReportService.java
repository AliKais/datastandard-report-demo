package com.stibo.demo.report.service;

import com.stibo.demo.report.logging.LogTime;
import com.stibo.demo.report.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ReportService {

    @LogTime
    public Stream<Stream<String>> report(Datastandard datastandard, String categoryId) {
        Stream<String> header = Stream.of(
                "Category",
                        "Attribute",
                        "Description",
                        "Type",
                        "Groups");

        Category category = datastandard.categories().stream()
                .filter(c -> c.id().equals(categoryId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Category not found: " + categoryId)
                        // TODO: MAP TO HTTP STATUS CODE 404
                );

        List<Category> categories = new ArrayList<>();
        categories.add(category);
        addParent(datastandard.categories(), categories, category);
        Collections.reverse(categories);

        return Stream.concat(
                Stream.of(header),
                categories.stream()
                        .flatMap(cat -> generateRow(datastandard, cat))
        );
    }

    void addParent(List<Category> allCategories, List<Category> parentCategories, Category childCategory) {
        final var parent = allCategories.stream()
                .filter(c -> c.id().equals(childCategory.parentId()))
                .findFirst();

        if (parent.isPresent()) {
            parentCategories.add(parent.get());
            addParent(allCategories, parentCategories, parent.get());
        }
    }

    Stream<Stream<String>> generateRow(Datastandard datastandard, Category category) {
        return category.attributeLinks().stream()
                .map(AttributeLink::id)
                .map(attributeId -> datastandard.attributes().stream()
                        .filter(a -> a.id().equals(attributeId))
                        .findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(attribute -> Stream.of(
                        category.name(),
                        formatAttributeName(category, attribute),
                        attribute.description(),
                        formatType(datastandard, attribute),
                        groupNames(datastandard.attributeGroups(), attribute)
                    )
                );
    }

    String formatAttributeName (Category category, Attribute attribute) {
        AttributeLink attributeLink = category.attributeLinks().stream()
                .filter(al -> al.id().equals(attribute.id()))
                .findFirst()
                .orElse(null);

        return attributeLink != null && attributeLink.optional() != null && attributeLink.optional()
                ? attribute.name()
                : attribute.name() + "*";
    }

    String formatType (Datastandard datastandard, Attribute attribute) {
        StringBuilder types = new StringBuilder();

        if (attribute.attributeLinks() != null && !attribute.attributeLinks().isEmpty()) {
            types.append(attribute.type().id()).append("{\n");

            attribute.attributeLinks().forEach(attLink -> {
                Attribute attributeType = findAttribute(datastandard, attLink.id());
                if (attributeType != null) {
                    String subType = formatType(datastandard, attributeType);
                    types.append("  ")
                            .append(attributeLinkOptional(attributeType.name(), attLink))
                            .append(": ")
                            .append(subType)
                            .append("\n");
                }
            });
            types.append("}");
        } else {
            types.append(attribute.type().id());
        }
        if (attribute.type().multiValue()) {
            types.append("[]");
        }
        return types.toString();
    }

    String groupNames (List<AttributeGroup> groups, Attribute attribute) {
        return attribute.groupIds().stream()
                .map(groupId -> groups.stream()
                        .filter(g -> g.id().equals(groupId))
                        .findFirst()
                        .map(AttributeGroup::name)
                        .orElse(null))
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    Attribute findAttribute (Datastandard datastandard, String attributeId) {
        return datastandard.attributes().stream()
                .filter(a -> a.id().equals(attributeId))
                .findFirst()
                .orElse(null);
    }

    String attributeLinkOptional(String name, AttributeLink link) {
        return link.optional() != null && link.optional() ? name : name + "*";
    }
}
