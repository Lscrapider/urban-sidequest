package com.urbansidequest.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.po.RouteSharePO;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteSegmentVO;
import com.urbansidequest.backend.domain.vo.RouteShareVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import com.urbansidequest.backend.manage.RouteExecutionManage;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.manage.RouteShareManage;
import com.urbansidequest.backend.provider.route.share.RouteShareImageObjectStore;
import com.urbansidequest.backend.provider.route.share.RouteShareImageObjectStore.StoredRouteShareImage;
import com.urbansidequest.backend.service.RouteShareService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RouteShareServiceImpl implements RouteShareService {

    private static final int MAX_SHARE_PAGE_SIZE = 50;

    private static final int STATIC_MAP_WIDTH = 720;

    private static final int STATIC_MAP_HEIGHT = 420;

    private static final int MAX_STATIC_MAP_POINTS = 90;

    private final RouteShareManage routeShareManage;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RouteExecutionManage routeExecutionManage;

    private final RouteShareImageObjectStore routeShareImageObjectStore;

    private final AmapWebProperties amapWebProperties;

    public RouteShareServiceImpl(
            RouteShareManage routeShareManage,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RouteExecutionManage routeExecutionManage,
            RouteShareImageObjectStore routeShareImageObjectStore,
            AmapWebProperties amapWebProperties
    ) {
        this.routeShareManage = routeShareManage;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routeExecutionManage = routeExecutionManage;
        this.routeShareImageObjectStore = routeShareImageObjectStore;
        this.amapWebProperties = amapWebProperties;
    }

    @Override
    public List<RouteShareVO> listLatestShares(int pageSize) {
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_SHARE_PAGE_SIZE);
        return this.routeShareManage.findLatest(normalizedPageSize).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public RouteShareVO shareCompletedRoute(
            AuthenticatedUser authenticatedUser,
            UUID requestId,
            String routeCode,
            String shareText,
            MultipartFile image
    ) {
        this.validateCompletedRoute(authenticatedUser.id(), requestId, routeCode);
        String normalizedShareText = StrUtil.blankToDefault(shareText, "这条路线走下来很顺，适合直接照着走。").trim();
        if (normalizedShareText.length() > 240) {
            throw new IllegalArgumentException("分享文字不能超过 240 个字");
        }
        String contentType = this.validateImageContentType(image);
        byte[] imageBytes = this.readImageBytes(image);
        StoredRouteShareImage storedImage = this.routeShareImageObjectStore.putShareImage(
                authenticatedUser.id(),
                requestId,
                routeCode,
                imageBytes,
                contentType
        );
        RouteSharePO share = this.routeShareManage.upsert(
                authenticatedUser.id(),
                requestId,
                routeCode,
                normalizedShareText,
                storedImage.imageUrl(),
                storedImage.objectKey()
        );
        return this.toVO(share);
    }

    @Override
    public RouteGenerationVO getSharedRoute(UUID shareId) {
        RouteSharePO share = this.routeShareManage.findByShareId(shareId)
                .orElseThrow(() -> new IllegalArgumentException("分享路线不存在"));
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(share.getUserId(), share.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("分享路线历史不存在"));
        return this.routeGenerationHistoryManage.toRouteGenerationVO(history);
    }

    @Override
    public byte[] buildRouteStaticMap(AuthenticatedUser authenticatedUser, UUID requestId, String routeCode) {
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(authenticatedUser.id(), requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        GeneratedRouteVO route = this.findRoute(this.routeGenerationHistoryManage.toRouteGenerationVO(history), routeCode);
        return this.fetchStaticMap(route);
    }

    private void validateCompletedRoute(UUID userId, UUID requestId, String routeCode) {
        RouteExecutionPO execution = this.routeExecutionManage.findLatestByRequestId(userId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线尚未完成，不能分享"));
        if (!routeCode.equals(execution.getRouteCode()) || execution.getExecutionStatus() != RouteExecutionStatus.COMPLETED) {
            throw new IllegalArgumentException("只能分享已经走完的路线");
        }
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(userId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        this.findRoute(this.routeGenerationHistoryManage.toRouteGenerationVO(history), routeCode);
    }

    private GeneratedRouteVO findRoute(RouteGenerationVO routeGeneration, String routeCode) {
        return routeGeneration.routes().stream()
                .filter(route -> route.routeCode().equals(routeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("路线不属于当前历史记录"));
    }

    private RouteShareVO toVO(RouteSharePO share) {
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(share.getUserId(), share.getRequestId())
                .orElse(null);
        RouteGenerationVO routeGeneration = history == null ? null : this.routeGenerationHistoryManage.toRouteGenerationVO(history);
        GeneratedRouteVO route = routeGeneration == null ? null : routeGeneration.routes().stream()
                .filter(candidate -> candidate.routeCode().equals(share.getRouteCode()))
                .findFirst()
                .orElse(null);
        return new RouteShareVO(
                share.getId(),
                share.getRequestId(),
                share.getRouteCode(),
                route == null ? "城市路线" : route.title(),
                history == null ? "城市副本路线" : history.getAreaLabel(),
                share.getShareText(),
                share.getImageUrl(),
                share.getCreatedAt()
        );
    }

    private byte[] readImageBytes(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请先生成分享图片");
        }
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("分享图片读取失败", exception);
        }
    }

    private String validateImageContentType(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请先生成分享图片");
        }
        String contentType = image.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("分享图片仅支持 JPEG 或 PNG");
        }
        return contentType;
    }

    private byte[] fetchStaticMap(GeneratedRouteVO route) {
        List<GeoPointVO> points = this.samplePoints(this.collectRoutePoints(route));
        if (points.isEmpty()) {
            throw new IllegalArgumentException("路线缺少地图坐标，无法生成分享图");
        }
        String key = this.amapWebProperties.effectiveKeys().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("高德 Web Key 未配置"));
        GeoPointVO center = this.centerOf(points);
        String url = "%s/v3/staticmap?location=%s&zoom=%d&size=%d*%d&scale=2&paths=%s&key=%s".formatted(
                StrUtil.removeSuffix(this.amapWebProperties.getBaseUrl(), "/"),
                this.encode("%s,%s".formatted(center.longitudeGcj02(), center.latitudeGcj02())),
                this.zoomOf(points),
                STATIC_MAP_WIDTH,
                STATIC_MAP_HEIGHT,
                this.encode(this.pathValue(points)),
                this.encode(key)
        );
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) this.amapWebProperties.getConnectTimeout().toMillis());
            connection.setReadTimeout((int) this.amapWebProperties.getReadTimeout().toMillis());
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("静态地图生成失败");
            }
            String contentType = StrUtil.blankToDefault(connection.getContentType(), "").toLowerCase();
            if (!contentType.startsWith("image/")) {
                throw new IllegalStateException("静态地图生成失败，请检查高德 Web Key 或路线坐标");
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("静态地图生成失败", exception);
        }
    }

    private List<GeoPointVO> collectRoutePoints(GeneratedRouteVO route) {
        List<GeoPointVO> points = new ArrayList<>();
        List<RouteSegmentVO> segments = route.segments() == null ? List.of() : route.segments();
        for (RouteSegmentVO segment : segments) {
            if (segment.polyline() != null && !segment.polyline().isEmpty()) {
                points.addAll(segment.polyline());
            } else if (segment.steps() != null) {
                segment.steps().stream()
                        .filter(step -> step.polyline() != null)
                        .flatMap(step -> step.polyline().stream())
                        .forEach(points::add);
            }
        }
        if (points.isEmpty()) {
            List<RouteStopVO> stops = route.stops() == null ? List.of() : route.stops();
            stops.stream()
                    .sorted(Comparator.comparingInt(RouteStopVO::order))
                    .map(RouteStopVO::location)
                    .forEach(points::add);
        }
        return points;
    }

    private List<GeoPointVO> samplePoints(List<GeoPointVO> points) {
        if (points.size() <= MAX_STATIC_MAP_POINTS) {
            return points;
        }
        List<GeoPointVO> sampled = new ArrayList<>();
        double step = (points.size() - 1) / (double) (MAX_STATIC_MAP_POINTS - 1);
        for (int index = 0; index < MAX_STATIC_MAP_POINTS; index++) {
            sampled.add(points.get((int) Math.round(index * step)));
        }
        return sampled;
    }

    private GeoPointVO centerOf(List<GeoPointVO> points) {
        double minLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).max().orElse(0d);
        double minLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).max().orElse(0d);
        return new GeoPointVO(
                BigDecimal.valueOf((minLon + maxLon) / 2d),
                BigDecimal.valueOf((minLat + maxLat) / 2d)
        );
    }

    private int zoomOf(List<GeoPointVO> points) {
        double lonSpan = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).max().orElse(0d)
                - points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).min().orElse(0d);
        double latSpan = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).max().orElse(0d)
                - points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).min().orElse(0d);
        double span = Math.max(lonSpan, latSpan);
        if (span <= 0.01d) {
            return 16;
        }
        if (span <= 0.02d) {
            return 15;
        }
        if (span <= 0.05d) {
            return 14;
        }
        if (span <= 0.1d) {
            return 13;
        }
        return 12;
    }

    private String pathValue(List<GeoPointVO> points) {
        String locations = points.stream()
                .map(point -> "%s,%s".formatted(point.longitudeGcj02(), point.latitudeGcj02()))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        return "7,0x1373E6,0.85,,:" + locations;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
