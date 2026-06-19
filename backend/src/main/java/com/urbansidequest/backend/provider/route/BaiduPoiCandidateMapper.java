package com.urbansidequest.backend.provider.route;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BaiduPoiCandidateMapper {

    public Optional<PoiCandidateDTO> toPoiCandidate(
            JsonNode poiNode,
            PoiCandidateRole role,
            String category,
            List<String> matchedInterestTags,
            String reasonSeed
    ) {
        String uid = this.firstText(poiNode, "uid");
        String name = this.firstText(poiNode, "name");
        JsonNode locationNode = poiNode.path("location");
        if (StrUtil.isBlank(name) || locationNode.isMissingNode()) {
            return Optional.empty();
        }
        BigDecimal lng = this.parseDecimal(this.firstText(locationNode, "lng"));
        BigDecimal lat = this.parseDecimal(this.firstText(locationNode, "lat"));
        if (lng == null || lat == null) {
            return Optional.empty();
        }

        String providerPoiId = StrUtil.isBlank(uid) ? "baidu-" + name + "-" + lng + "," + lat : "baidu-" + uid;
        String address = this.firstText(poiNode, "address", "province", "city", "area");
        String rawType = this.firstText(poiNode, "detail_info.type", "detail_info.tag", "type", "tag");
        String shopHours = this.firstText(poiNode, "detail_info.shop_hours", "shop_hours");
        String keytag = this.firstText(poiNode, "detail_info.tag", "detail_info.label", "tag");
        String rectag = this.firstText(poiNode, "detail_info.classified_poi_tag", "classified_poi_tag");
        BigDecimal rating = this.parseDecimal(this.firstText(poiNode, "detail_info.overall_rating", "overall_rating"));
        Integer avgPriceCent = this.parseAvgPriceCent(this.firstText(poiNode, "detail_info.price", "price"));
        Integer distanceMeters = this.parseInteger(this.firstText(poiNode, "detail_info.distance", "distance"));

        return Optional.of(new PoiCandidateDTO(
                providerPoiId,
                providerPoiId,
                name,
                StrUtil.blankToDefault(category, role.name()),
                role,
                new GeoPointDTO(lng, lat),
                address,
                this.buildPoiDescription(address, rawType, rating, avgPriceCent, reasonSeed),
                rating,
                avgPriceCent,
                matchedInterestTags,
                this.parsePhotoUrls(poiNode),
                this.parseInteger(this.firstText(poiNode, "detail_info.image_num", "image_num")),
                rawType,
                null,
                shopHours,
                shopHours,
                keytag,
                rectag,
                distanceMeters,
                List.of(),
                "UNKNOWN",
                null,
                false,
                reasonSeed
        ));
    }

    private String buildPoiDescription(
            String address,
            String rawType,
            BigDecimal rating,
            Integer avgPriceCent,
            String reasonSeed
    ) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(address)) {
            parts.add("位于" + address);
        }
        if (StrUtil.isNotBlank(rawType)) {
            parts.add("类型为" + rawType);
        }
        if (rating != null) {
            parts.add("百度评分 " + rating.stripTrailingZeros().toPlainString());
        }
        if (avgPriceCent != null) {
            parts.add("人均约 " + avgPriceCent / 100 + " 元");
        }
        if (StrUtil.isNotBlank(reasonSeed)) {
            parts.add(reasonSeed);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join("，", parts) + "。";
    }

    private List<String> parsePhotoUrls(JsonNode poiNode) {
        List<String> photoUrls = new ArrayList<>();
        JsonNode photos = poiNode.path("detail_info").path("photos");
        if (!photos.isArray()) {
            photos = poiNode.path("photos");
        }
        if (photos.isArray()) {
            for (JsonNode photo : photos) {
                String url = this.firstText(photo, "url");
                if (StrUtil.isNotBlank(url)) {
                    photoUrls.add(url);
                }
            }
        }
        return photoUrls.stream().distinct().limit(6).toList();
    }

    private Integer parseAvgPriceCent(String value) {
        BigDecimal price = this.parseDecimal(value);
        if (price == null) {
            return null;
        }
        return price.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                current = current.path(segment);
            }
            if (!current.isMissingNode() && StrUtil.isNotBlank(current.asText())) {
                return current.asText();
            }
        }
        return null;
    }
}
