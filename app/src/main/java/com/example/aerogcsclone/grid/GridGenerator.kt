package com.example.aerogcsclone.grid

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/**
 * Main grid generator for survey missions
 * Based on MissionPlanner grid algorithm with obstacle avoidance
 */
class GridGenerator {

    /**
     * Generate grid survey waypoints for a given polygon
     * @param polygon Survey area boundary
     * @param params Grid parameters (spacing, angle, speed, altitude)
     * @return GridSurveyResult containing waypoints and metadata
     */
    fun generateGridSurvey(
        polygon: List<LatLng>,
        params: GridSurveyParams
    ): GridSurveyResult {
        if (polygon.size < 3) {
            return GridSurveyResult(
                waypoints = emptyList(),
                gridLines = emptyList(),
                totalDistance = 0.0,
                estimatedTime = 0.0,
                numLines = 0,
                polygonArea = "0 ft²"
            )
        }

        // Apply indentation (shrink polygon inward for safe zone)
        val effectivePolygon = if (params.indentation > 0) {
            GridUtils.shrinkPolygon(polygon, params.indentation)
        } else {
            polygon
        }

        // Calculate polygon center and bounding box
        val center = GridUtils.calculatePolygonCenter(effectivePolygon)
        val (southwest, northeast) = GridUtils.calculateBoundingBox(effectivePolygon)

        // Calculate grid dimensions
        val width = GridUtils.haversineDistance(
            LatLng(southwest.latitude, southwest.longitude),
            LatLng(southwest.latitude, northeast.longitude)
        )
        val height = GridUtils.haversineDistance(
            LatLng(southwest.latitude, southwest.longitude),
            LatLng(northeast.latitude, southwest.longitude)
        )

        // Determine grid angle - use user input or auto-calculate from longest side
        val gridAngleRad = if (params.gridAngle == 0f) {
            Math.toRadians(GridUtils.getAngleOfLongestSide(polygon))
        } else {
            Math.toRadians(params.gridAngle.toDouble())
        }

        // Calculate the maximum dimension to ensure full coverage
        val maxDimension = max(width, height) * 1.5

        // Calculate number of lines needed
        val numLines = ceil(maxDimension / params.lineSpacing).toInt()

        val gridLines = mutableListOf<Pair<LatLng, LatLng>>()
        val waypoints = mutableListOf<GridWaypoint>()

        // Pre-process obstacles: expand by LARGER buffer (minimum 3 meters for safety)
        val effectiveBuffer = maxOf(params.obstacleBoundary.toDouble(), 3.0)
        val expandedObstacles = params.obstacles.mapNotNull { obstacle ->
            if (obstacle.size >= 3) {
                expandPolygonEdgeBased(obstacle, effectiveBuffer)
            } else null
        }
        val originalObstacles = params.obstacles.filter { it.size >= 3 }

        // Collect all valid line segments first
        data class GridSegment(
            val start: LatLng,
            val end: LatLng,
            val lineIndex: Int,
            val segmentIndex: Int,
            // Position of segment relative to obstacles on the line (0 = first/before, 1 = after, etc.)
            val relativePosition: Int = 0
        )

        val allSegments = mutableListOf<GridSegment>()

        // Generate grid lines and split around obstacles
        for (i in 0 until numLines) {
            val offset = (i - numLines / 2.0) * params.lineSpacing

            val perpOffsetX = offset * cos(gridAngleRad + PI/2)
            val perpOffsetY = offset * sin(gridAngleRad + PI/2)

            val lineLength = maxDimension
            val lineOffsetX = lineLength/2 * cos(gridAngleRad)
            val lineOffsetY = lineLength/2 * sin(gridAngleRad)

            val lineStart = GridUtils.moveLatLng(
                center,
                perpOffsetX - lineOffsetX,
                perpOffsetY - lineOffsetY
            )
            val lineEnd = GridUtils.moveLatLng(
                center,
                perpOffsetX + lineOffsetX,
                perpOffsetY + lineOffsetY
            )

            // Trim line to polygon intersection
            val trimmedLine = trimLineToPolygon(lineStart, lineEnd, effectivePolygon)

            if (trimmedLine != null) {
                val (start, end) = trimmedLine

                // Split the line if it intersects with any obstacles
                val lineSegments = if (params.obstacles.isNotEmpty()) {
                    splitLineAroundObstacles(start, end, originalObstacles, expandedObstacles)
                } else {
                    listOf(Pair(start, end))
                }

                // Add all segments for this line with relative position
                lineSegments.forEachIndexed { segIdx, segment ->
                    allSegments.add(GridSegment(
                        start = segment.first,
                        end = segment.second,
                        lineIndex = i,
                        segmentIndex = segIdx,
                        relativePosition = segIdx  // 0 = before obstacle, 1+ = after obstacle
                    ))
                }
            }
        }

        // ===== IMPROVED OBSTACLE-AWARE ORDERING =====
        // Strategy based on reference app:
        // 1. Complete ALL lines on one side of obstacle (Zone 0) with boustrophedon
        // 2. Transition at the TOP or BOTTOM edge of obstacle (where no grid lines exist)
        //    ADD: Insert transition waypoints along obstacle boundary to avoid diagonal crossing
        // 3. Complete ALL lines on the other side (Zone 1) with boustrophedon
        //
        // Key: Zone 0 ends at the LAST line (highest index), Zone 1 STARTS at the LAST line
        // This way transition happens at the edge, not crossing through middle lines

        // Check if we have actual split segments (obstacles caused line splits)
        val hasMultipleSegmentsPerLine = allSegments.groupBy { it.lineIndex }.any { it.value.size > 1 }
        val hasObstacles = params.obstacles.isNotEmpty() && hasMultipleSegmentsPerLine

        if (hasObstacles) {
            // Find max relative position (number of obstacle crossings)
            val maxRelativePosition = allSegments.maxOfOrNull { it.relativePosition } ?: 0

            // Track the last waypoint added for zone transitions
            var lastZoneEndPoint: LatLng? = null

            // Process each "zone" separately
            for (zone in 0..maxRelativePosition) {
                val zoneSegments = allSegments.filter { it.relativePosition == zone }
                if (zoneSegments.isEmpty()) continue

                // Get unique line indices in this zone
                val lineIndicesInZone = zoneSegments.map { it.lineIndex }.distinct()

                // CRITICAL: Both zones process in the SAME direction (ascending)
                // But Zone 1 should start from where Zone 0 ended
                // Zone 0: process 1->2->3->...->N (ends at line N, top of field)
                // Zone 1: process N->N-1->...->1 (starts at line N, goes down)
                val orderedLineIndices = if (zone == 0) {
                    lineIndicesInZone.sorted()  // Ascending: 1, 2, 3, ... N
                } else {
                    lineIndicesInZone.sortedDescending()  // Descending: N, N-1, ... 1
                }

                // Determine the first point of this zone for transition calculation
                var firstZoneStartPoint: LatLng? = null

                // Process lines in this zone with boustrophedon pattern
                for ((zoneLineNum, lineIdx) in orderedLineIndices.withIndex()) {
                    val lineSegments = zoneSegments.filter { it.lineIndex == lineIdx }

                    // Alternate direction based on zone-local line number for boustrophedon
                    val reverseDirection = zoneLineNum % 2 == 1

                    // Sort segments by position along the line
                    val sortedSegments = lineSegments.sortedBy { seg ->
                        seg.start.latitude + seg.start.longitude
                    }
                    val orderedSegments = if (reverseDirection) sortedSegments.reversed() else sortedSegments

                    for (segment in orderedSegments) {
                        // Determine segment direction based on boustrophedon
                        val (segStart, segEnd) = if (reverseDirection) {
                            Pair(segment.end, segment.start)
                        } else {
                            Pair(segment.start, segment.end)
                        }

                        // Capture first point of the zone for transition routing
                        if (firstZoneStartPoint == null) {
                            firstZoneStartPoint = segStart
                        }

                        // ===== ADD TRANSITION WAYPOINTS AROUND OBSTACLES =====
                        // If this is the first segment of a new zone (zone > 0) and we have a last endpoint from previous zone,
                        // add transition waypoints that go around the obstacle boundary instead of direct diagonal flight
                        if (zone > 0 && zoneLineNum == 0 && waypoints.isNotEmpty()) {
                            lastZoneEndPoint?.let { prevEndPoint ->
                                val transitionWaypoints = calculateTransitionWaypointsAroundObstacle(
                                    prevEndPoint,
                                    segStart,
                                    expandedObstacles
                                )

                                // Add transition waypoints (excluding start and end which are already handled)
                                for (transitionPoint in transitionWaypoints) {
                                    waypoints.add(GridWaypoint(
                                        position = transitionPoint,
                                        altitude = params.altitude,
                                        speed = if (params.includeSpeedCommands) params.speed else null,
                                        isLineStart = false,
                                        isLineEnd = false,
                                        isTransition = true,  // Mark as transition waypoint
                                        lineIndex = gridLines.size  // Use next line index
                                    ))
                                }
                            }
                        }

                        // Add the grid line for visualization
                        gridLines.add(Pair(segStart, segEnd))

                        // Add waypoints
                        waypoints.add(GridWaypoint(
                            position = segStart,
                            altitude = params.altitude,
                            speed = if (params.includeSpeedCommands) params.speed else null,
                            isLineStart = true,
                            lineIndex = gridLines.size - 1
                        ))

                        waypoints.add(GridWaypoint(
                            position = segEnd,
                            altitude = params.altitude,
                            speed = if (params.includeSpeedCommands) params.speed else null,
                            isLineEnd = true,
                            lineIndex = gridLines.size - 1
                        ))

                        // Track the last endpoint for zone transition
                        lastZoneEndPoint = segEnd
                    }
                }
            }
        } else {
            // No obstacles - use original boustrophedon order
            val segmentsByLine = allSegments.groupBy { it.lineIndex }
            val sortedLineIndices = segmentsByLine.keys.sorted()

            var actualLineIndex = 0
            val processedSegments = mutableSetOf<GridSegment>()

            for ((lineNum, lineIdx) in sortedLineIndices.withIndex()) {
                val lineSegments = segmentsByLine[lineIdx] ?: continue

                // Sort segments by position along the line
                val sortedSegments = lineSegments.sortedBy { seg ->
                    seg.start.latitude + seg.start.longitude
                }

                // Reverse direction for odd lines (boustrophedon pattern)
                val reverseDirection = lineNum % 2 == 1
                val orderedSegments = if (reverseDirection) sortedSegments.reversed() else sortedSegments

                for (segment in orderedSegments) {
                    if (segment in processedSegments) continue
                    processedSegments.add(segment)

                    // Determine segment direction
                    val (segStart, segEnd) = if (reverseDirection) {
                        Pair(segment.end, segment.start)
                    } else {
                        Pair(segment.start, segment.end)
                    }

                    // Add the grid line for visualization
                    gridLines.add(Pair(segStart, segEnd))

                    // Add waypoints
                    waypoints.add(GridWaypoint(
                        position = segStart,
                        altitude = params.altitude,
                        speed = if (params.includeSpeedCommands) params.speed else null,
                        isLineStart = true,
                        lineIndex = actualLineIndex
                    ))

                    waypoints.add(GridWaypoint(
                        position = segEnd,
                        altitude = params.altitude,
                        speed = if (params.includeSpeedCommands) params.speed else null,
                        isLineEnd = true,
                        lineIndex = actualLineIndex
                    ))

                    actualLineIndex++
                }
            }
        }

        // Calculate total distance and time
        val totalDistance = calculateTotalDistance(waypoints)
        val estimatedTime = if (params.speed > 0) totalDistance / params.speed else 0.0
        val polygonArea = GridUtils.calculateAndFormatPolygonArea(polygon)

        return GridSurveyResult(
            waypoints = waypoints,
            gridLines = gridLines,
            totalDistance = totalDistance,
            estimatedTime = estimatedTime,
            numLines = gridLines.size,
            polygonArea = polygonArea
        )
    }


    /**
     * Trim a line to intersect with polygon boundaries
     */
    private fun trimLineToPolygon(
        lineStart: LatLng,
        lineEnd: LatLng,
        polygon: List<LatLng>
    ): Pair<LatLng, LatLng>? {
        val numSamples = 100
        val validPoints = mutableListOf<LatLng>()

        for (i in 0..numSamples) {
            val t = i.toDouble() / numSamples
            val lat = lineStart.latitude + t * (lineEnd.latitude - lineStart.latitude)
            val lng = lineStart.longitude + t * (lineEnd.longitude - lineStart.longitude)
            val point = LatLng(lat, lng)

            if (GridUtils.isPointInPolygon(point, polygon)) {
                validPoints.add(point)
            }
        }

        return if (validPoints.isNotEmpty()) {
            Pair(validPoints.first(), validPoints.last())
        } else {
            null
        }
    }

    /**
     * Split a line around obstacle zones
     * Returns segments that are OUTSIDE obstacles
     */
    private fun splitLineAroundObstacles(
        start: LatLng,
        end: LatLng,
        originalObstacles: List<List<LatLng>>,
        expandedObstacles: List<List<LatLng>>
    ): List<Pair<LatLng, LatLng>> {
        // Use very high sampling for accurate detection
        val numSamples = 1000
        val segments = mutableListOf<Pair<LatLng, LatLng>>()

        // Combine original and expanded obstacles - check both
        val allObstaclesToCheck = originalObstacles + expandedObstacles

        if (allObstaclesToCheck.isEmpty()) {
            return listOf(Pair(start, end))
        }

        // Sample points along the line and check if each is inside any obstacle
        val pointsAlongLine = mutableListOf<Pair<LatLng, Boolean>>()

        for (i in 0..numSamples) {
            val t = i.toDouble() / numSamples
            val lat = start.latitude + t * (end.latitude - start.latitude)
            val lng = start.longitude + t * (end.longitude - start.longitude)
            val point = LatLng(lat, lng)

            var isInsideAnyObstacle = false

            // Check against all obstacles using multiple algorithms for robustness
            for (obstacle in allObstaclesToCheck) {
                if (obstacle.size >= 3) {
                    // Use both winding number and ray casting for maximum accuracy
                    val insideByWinding = isPointInsidePolygonWinding(point, obstacle)
                    val insideByRayCast = isPointInPolygonRobust(point, obstacle)
                    if (insideByWinding || insideByRayCast) {
                        isInsideAnyObstacle = true
                        break
                    }
                }
            }

            // isValid = true means point is OUTSIDE all obstacles (safe to fly)
            pointsAlongLine.add(Pair(point, !isInsideAnyObstacle))
        }

        // Build segments from consecutive valid (outside obstacle) points
        var segmentStart: LatLng? = null
        var lastValidPoint: LatLng? = null
        var validPointCount = 0

        for ((point, isValid) in pointsAlongLine) {
            if (isValid) {
                if (segmentStart == null) {
                    segmentStart = point
                }
                lastValidPoint = point
                validPointCount++
            } else {
                // End of valid segment - we hit an obstacle
                if (segmentStart != null && lastValidPoint != null && validPointCount >= 3) {
                    val segLength = GridUtils.haversineDistance(segmentStart, lastValidPoint)
                    if (segLength >= 0.5) { // At least 0.5 meter segment
                        segments.add(Pair(segmentStart, lastValidPoint))
                    }
                }
                segmentStart = null
                lastValidPoint = null
                validPointCount = 0
            }
        }

        // Add final segment if exists
        if (segmentStart != null && lastValidPoint != null && validPointCount >= 3) {
            val segLength = GridUtils.haversineDistance(segmentStart, lastValidPoint)
            if (segLength >= 0.5) {
                segments.add(Pair(segmentStart, lastValidPoint))
            }
        }

        // If no segments found but entire line is valid, return original line
        return if (segments.isEmpty() && pointsAlongLine.all { it.second }) {
            listOf(Pair(start, end))
        } else if (segments.isEmpty()) {
            // Entire line is inside obstacle(s)
            emptyList()
        } else {
            segments
        }
    }

    /**
     * Winding number algorithm for point-in-polygon test
     * More robust than ray casting for complex polygons
     */
    private fun isPointInsidePolygonWinding(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        val x = point.longitude
        val y = point.latitude
        var windingNumber = 0

        for (i in polygon.indices) {
            val x1 = polygon[i].longitude
            val y1 = polygon[i].latitude
            val x2 = polygon[(i + 1) % polygon.size].longitude
            val y2 = polygon[(i + 1) % polygon.size].latitude

            if (y1 <= y) {
                if (y2 > y) {
                    // Upward crossing
                    val cross = (x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)
                    if (cross > 0) {
                        windingNumber++
                    }
                }
            } else {
                if (y2 <= y) {
                    // Downward crossing
                    val cross = (x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)
                    if (cross < 0) {
                        windingNumber--
                    }
                }
            }
        }

        return windingNumber != 0
    }

    /**
     * Robust point-in-polygon test using ray casting
     */
    private fun isPointInPolygonRobust(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        val x = point.longitude
        val y = point.latitude
        var inside = false

        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            if (abs(x - xi) < 1e-10 && abs(y - yi) < 1e-10) {
                return true
            }

            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)

            if (intersect) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * Expand a polygon outward by buffer distance
     */
    private fun expandPolygonEdgeBased(polygon: List<LatLng>, bufferMeters: Double): List<LatLng> {
        if (polygon.size < 3 || bufferMeters <= 0) return polygon

        val n = polygon.size
        val expanded = mutableListOf<LatLng>()

        val centroidLat = polygon.map { it.latitude }.average()
        val centroidLon = polygon.map { it.longitude }.average()

        for (i in 0 until n) {
            val prev = polygon[(i - 1 + n) % n]
            val curr = polygon[i]
            val next = polygon[(i + 1) % n]

            val bufferLatDeg = bufferMeters / 111111.0
            val bufferLonDeg = bufferMeters / (111111.0 * cos(Math.toRadians(curr.latitude)))

            val edge1Lat = curr.latitude - prev.latitude
            val edge1Lon = curr.longitude - prev.longitude
            val edge2Lat = next.latitude - curr.latitude
            val edge2Lon = next.longitude - curr.longitude

            val len1 = sqrt(edge1Lat * edge1Lat + edge1Lon * edge1Lon)
            val len2 = sqrt(edge2Lat * edge2Lat + edge2Lon * edge2Lon)

            if (len1 < 1e-10 || len2 < 1e-10) {
                val dirLat = curr.latitude - centroidLat
                val dirLon = curr.longitude - centroidLon
                val dirLen = sqrt(dirLat * dirLat + dirLon * dirLon)
                if (dirLen > 1e-10) {
                    expanded.add(LatLng(
                        curr.latitude + (dirLat / dirLen) * bufferLatDeg,
                        curr.longitude + (dirLon / dirLen) * bufferLonDeg
                    ))
                } else {
                    expanded.add(curr)
                }
                continue
            }

            val n1Lat = edge1Lat / len1
            val n1Lon = edge1Lon / len1
            val n2Lat = edge2Lat / len2
            val n2Lon = edge2Lon / len2

            var perp1Lat = -n1Lon
            var perp1Lon = n1Lat
            var perp2Lat = -n2Lon
            var perp2Lon = n2Lat

            val midEdge1Lat = (prev.latitude + curr.latitude) / 2
            val midEdge1Lon = (prev.longitude + curr.longitude) / 2
            val testPointLat = midEdge1Lat + perp1Lat * 0.0001
            val testPointLon = midEdge1Lon + perp1Lon * 0.0001

            val distOriginal = sqrt((midEdge1Lat - centroidLat).pow(2) + (midEdge1Lon - centroidLon).pow(2))
            val distTest = sqrt((testPointLat - centroidLat).pow(2) + (testPointLon - centroidLon).pow(2))

            if (distTest < distOriginal) {
                perp1Lat = -perp1Lat
                perp1Lon = -perp1Lon
                perp2Lat = -perp2Lat
                perp2Lon = -perp2Lon
            }

            var avgLat = perp1Lat + perp2Lat
            var avgLon = perp1Lon + perp2Lon
            val avgLen = sqrt(avgLat * avgLat + avgLon * avgLon)

            if (avgLen < 1e-10) {
                avgLat = perp1Lat
                avgLon = perp1Lon
            } else {
                avgLat /= avgLen
                avgLon /= avgLen
            }

            val dot = perp1Lat * avgLat + perp1Lon * avgLon
            val offsetMult = if (dot > 0.3) minOf(1.0 / dot, 2.5) else 2.5

            val newLat = curr.latitude + avgLat * bufferLatDeg * offsetMult
            val newLon = curr.longitude + avgLon * bufferLonDeg * offsetMult

            expanded.add(LatLng(newLat, newLon))
        }

        return expanded
    }

    /**
     * Calculate total distance of waypoint path
     */
    private fun calculateTotalDistance(waypoints: List<GridWaypoint>): Double {
        if (waypoints.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until waypoints.size - 1) {
            totalDistance += GridUtils.haversineDistance(
                waypoints[i].position,
                waypoints[i + 1].position
            )
        }
        return totalDistance
    }

    /**
     * Generate a simple rectangular survey pattern for testing
     */
    fun generateRectangularSurvey(
        center: LatLng,
        width: Double,
        height: Double,
        params: GridSurveyParams
    ): GridSurveyResult {
        val halfWidth = width / 2
        val halfHeight = height / 2

        val polygon = listOf(
            GridUtils.moveLatLng(center, -halfWidth, -halfHeight),
            GridUtils.moveLatLng(center, halfWidth, -halfHeight),
            GridUtils.moveLatLng(center, halfWidth, halfHeight),
            GridUtils.moveLatLng(center, -halfWidth, halfHeight)
        )

        return generateGridSurvey(polygon, params)
    }

    /**
     * Auto-calculate optimal grid angle based on polygon shape
     */
    fun calculateOptimalGridAngle(polygon: List<LatLng>): Float {
        if (polygon.size < 3) return 0f
        val longestSideAngle = GridUtils.getAngleOfLongestSide(polygon)
        return ((longestSideAngle + 90) % 360).toFloat()
    }

    /**
     * Estimate coverage area for given parameters
     */
    fun estimateCoverage(polygon: List<LatLng>, lineSpacing: Float): Float {
        val area = GridUtils.calculatePolygonArea(polygon)
        if (area <= 0) return 0f
        val estimatedCoveredArea = area * (1.0 - lineSpacing / 100.0)
        return (estimatedCoveredArea / area * 100).coerceIn(0.0, 100.0).toFloat()
    }

    /**
     * Calculate transition waypoints around obstacle boundaries.
     * This ensures the drone routes around the obstacle edge instead of flying diagonally across it.
     *
     * Strategy:
     * 1. Find which obstacle lies between start and end points
     * 2. Determine the closest edge of that obstacle to both points
     * 3. Generate waypoints along that edge (either top or bottom)
     *
     * @param start The ending point of Zone 0 (last point before transition)
     * @param end The starting point of Zone 1 (first point after transition)
     * @param expandedObstacles The obstacle polygons expanded by buffer
     * @return List of intermediate waypoints to follow the obstacle boundary
     */
    private fun calculateTransitionWaypointsAroundObstacle(
        start: LatLng,
        end: LatLng,
        expandedObstacles: List<List<LatLng>>
    ): List<LatLng> {
        if (expandedObstacles.isEmpty()) return emptyList()

        val transitionPoints = mutableListOf<LatLng>()

        // Find which obstacle is between start and end
        var relevantObstacle: List<LatLng>? = null
        for (obstacle in expandedObstacles) {
            if (obstacle.size < 3) continue
            // Check if direct path from start to end would cross this obstacle
            if (lineIntersectsObstacle(start, end, obstacle)) {
                relevantObstacle = obstacle
                break
            }
        }

        if (relevantObstacle == null || relevantObstacle.size < 3) {
            return emptyList()
        }

        // Determine if we should go around the top or bottom of the obstacle
        // Calculate centroid of obstacle
        val obsCentroidLat = relevantObstacle.map { it.latitude }.average()

        // Determine the "direction" of the transition (up or down based on latitude)
        // If start is below end (going up), we should go around the top
        // If start is above end (going down), we should go around the bottom
        val goingUp = start.latitude < end.latitude

        // Sort obstacle points by latitude to find top and bottom vertices
        val sortedByLat = relevantObstacle.sortedBy { it.latitude }

        // Find corner points to route around
        val bottomPoints = sortedByLat.take(2).sortedBy { it.longitude }
        val topPoints = sortedByLat.takeLast(2).sortedBy { it.longitude }

        // Choose route based on which edge is closest to our start/end points
        // and which direction we're transitioning
        val startToObsCenterLat = obsCentroidLat - start.latitude

        // Determine which edge to follow (top or bottom)
        val useTopEdge = if (goingUp) {
            // Going up - prefer top edge if start is closer to bottom
            startToObsCenterLat > 0
        } else {
            // Going down - prefer bottom edge if start is closer to top
            startToObsCenterLat < 0
        }

        val edgePoints = if (useTopEdge) topPoints else bottomPoints

        // Find the closest point on the edge to start
        val closestToStart = edgePoints.minByOrNull {
            GridUtils.haversineDistance(start, it)
        } ?: return emptyList()

        // Find the closest point on the edge to end
        val closestToEnd = edgePoints.minByOrNull {
            GridUtils.haversineDistance(end, it)
        } ?: return emptyList()

        // Add edge points in order from start to end
        if (closestToStart != closestToEnd) {
            // Add both corner points
            val distStartToFirst = GridUtils.haversineDistance(start, edgePoints[0])
            val distStartToSecond = GridUtils.haversineDistance(start, edgePoints[1])

            if (distStartToFirst < distStartToSecond) {
                transitionPoints.add(edgePoints[0])
                transitionPoints.add(edgePoints[1])
            } else {
                transitionPoints.add(edgePoints[1])
                transitionPoints.add(edgePoints[0])
            }
        } else {
            // Just add the single closest point
            transitionPoints.add(closestToStart)
        }

        return transitionPoints
    }

    /**
     * Check if a line from start to end intersects with an obstacle polygon
     */
    private fun lineIntersectsObstacle(start: LatLng, end: LatLng, obstacle: List<LatLng>): Boolean {
        if (obstacle.size < 3) return false

        // Check if the line passes through the obstacle
        val numSamples = 20
        for (i in 1 until numSamples) {
            val t = i.toDouble() / numSamples
            val lat = start.latitude + t * (end.latitude - start.latitude)
            val lng = start.longitude + t * (end.longitude - start.longitude)
            val point = LatLng(lat, lng)

            if (isPointInsidePolygonWinding(point, obstacle) ||
                isPointInPolygonRobust(point, obstacle)) {
                return true
            }
        }

        // Also check if line segment intersects any edge of the polygon
        for (i in obstacle.indices) {
            val p1 = obstacle[i]
            val p2 = obstacle[(i + 1) % obstacle.size]
            if (lineSegmentsIntersect(start, end, p1, p2)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if two line segments intersect
     */
    private fun lineSegmentsIntersect(
        a1: LatLng, a2: LatLng,
        b1: LatLng, b2: LatLng
    ): Boolean {
        val d1 = direction(b1, b2, a1)
        val d2 = direction(b1, b2, a2)
        val d3 = direction(a1, a2, b1)
        val d4 = direction(a1, a2, b2)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true
        }

        return false
    }

    /**
     * Calculate the cross product direction
     */
    private fun direction(pi: LatLng, pj: LatLng, pk: LatLng): Double {
        return (pk.longitude - pi.longitude) * (pj.latitude - pi.latitude) -
               (pj.longitude - pi.longitude) * (pk.latitude - pi.latitude)
    }
}
