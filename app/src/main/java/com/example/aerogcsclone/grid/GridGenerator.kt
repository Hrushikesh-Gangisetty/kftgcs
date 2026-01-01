package com.example.aerogcsclone.grid

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/**
 * Main grid generator for survey missions
 * Based on MissionPlanner grid algorithm
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
        val maxDimension = max(width, height) * 1.5 // Add buffer for rotation

        // Calculate number of lines needed
        val numLines = ceil(maxDimension / params.lineSpacing).toInt()

        val gridLines = mutableListOf<Pair<LatLng, LatLng>>()
        val waypoints = mutableListOf<GridWaypoint>()

        // CRITICAL: Use a separate counter for actual grid lines that intersect polygon
        // This ensures lineIndex matches the gridLines array index
        var actualLineIndex = 0

        // Generate grid lines
        for (i in 0 until numLines) {
            val offset = (i - numLines / 2.0) * params.lineSpacing

            // Calculate perpendicular offset based on grid angle
            val perpOffsetX = offset * cos(gridAngleRad + PI/2)
            val perpOffsetY = offset * sin(gridAngleRad + PI/2)

            // Calculate line endpoints
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

            // Trim line to polygon intersection (use effectivePolygon with indentation applied)
            val trimmedLine = trimLineToPolygon(lineStart, lineEnd, effectivePolygon)

            if (trimmedLine != null) {
                val (start, end) = trimmedLine

                // Split the line if it intersects with any obstacles
                val lineSegments = if (params.obstacles.isNotEmpty()) {
                    splitLineAroundObstacles(start, end, params.obstacles, params.obstacleBoundary)
                } else {
                    listOf(Pair(start, end))
                }

                // Process each segment (may be multiple if split by obstacles)
                for (segment in lineSegments) {
                    val (segStart, segEnd) = segment
                    gridLines.add(Pair(segStart, segEnd))

                    // Alternate direction for boustrophedon pattern (back and forth)
                    // Use actualLineIndex for alternation to maintain proper back-and-forth pattern
                    val (waypointStart, waypointEnd) = if (actualLineIndex % 2 == 0) {
                        Pair(segStart, segEnd)
                    } else {
                        Pair(segEnd, segStart)
                    }

                    // Add waypoints for this line
                    // CRITICAL: Use actualLineIndex (not i) to ensure lineIndex matches gridLines index
                    waypoints.add(GridWaypoint(
                        position = waypointStart,
                        altitude = params.altitude,
                        speed = if (params.includeSpeedCommands) params.speed else null,
                        isLineStart = true,
                        lineIndex = actualLineIndex
                    ))

                    waypoints.add(GridWaypoint(
                        position = waypointEnd,
                        altitude = params.altitude,
                        speed = if (params.includeSpeedCommands) params.speed else null,
                        isLineEnd = true,
                        lineIndex = actualLineIndex
                    ))

                    // Increment actual line counter after adding a valid line
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
     * Uses a simplified approach - checks multiple points along the line
     */
    private fun trimLineToPolygon(
        lineStart: LatLng,
        lineEnd: LatLng,
        polygon: List<LatLng>
    ): Pair<LatLng, LatLng>? {
        val numSamples = 100
        val validPoints = mutableListOf<LatLng>()

        // Sample points along the line
        for (i in 0..numSamples) {
            val t = i.toDouble() / numSamples
            val lat = lineStart.latitude + t * (lineEnd.latitude - lineStart.latitude)
            val lng = lineStart.longitude + t * (lineEnd.longitude - lineStart.longitude)
            val point = LatLng(lat, lng)

            if (GridUtils.isPointInPolygon(point, polygon)) {
                validPoints.add(point)
            }
        }

        // Return first and last valid points if any exist
        return if (validPoints.isNotEmpty()) {
            Pair(validPoints.first(), validPoints.last())
        } else {
            null
        }
    }

    /**
     * Split a line around obstacle zones
     * Returns a list of line segments that avoid the obstacles
     *
     * @param start Start point of the line
     * @param end End point of the line
     * @param obstacles List of obstacle polygons
     * @param boundaryBuffer Buffer distance in meters to maintain from obstacles
     * @return List of line segments that don't intersect obstacles
     */
    private fun splitLineAroundObstacles(
        start: LatLng,
        end: LatLng,
        obstacles: List<List<LatLng>>,
        boundaryBuffer: Float
    ): List<Pair<LatLng, LatLng>> {
        // Use higher sampling for better accuracy
        val numSamples = 200
        val segments = mutableListOf<Pair<LatLng, LatLng>>()

        // Pre-process obstacles: order points and expand by buffer
        val expandedObstacles = obstacles.mapNotNull { obstacle ->
            if (obstacle.size >= 3) {
                val ordered = GridUtils.orderPointsClockwise(obstacle)
                expandPolygon(ordered, boundaryBuffer.toDouble())
            } else null
        }

        if (expandedObstacles.isEmpty()) {
            return listOf(Pair(start, end))
        }

        // Sample points along the line and mark which are inside obstacles
        val pointsAlongLine = mutableListOf<Pair<LatLng, Boolean>>() // Point and whether it's valid (not in obstacle)

        for (i in 0..numSamples) {
            val t = i.toDouble() / numSamples
            val lat = start.latitude + t * (end.latitude - start.latitude)
            val lng = start.longitude + t * (end.longitude - start.longitude)
            val point = LatLng(lat, lng)

            // Check if point is inside any expanded obstacle
            var isInsideObstacle = false
            for (expandedObstacle in expandedObstacles) {
                if (GridUtils.isPointInPolygon(point, expandedObstacle)) {
                    isInsideObstacle = true
                    break
                }
            }

            pointsAlongLine.add(Pair(point, !isInsideObstacle))
        }

        // Build segments from consecutive valid points
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
                // End of a valid segment - require minimum length (at least 3 sample points)
                if (segmentStart != null && lastValidPoint != null && validPointCount >= 3) {
                    val segLength = GridUtils.haversineDistance(segmentStart, lastValidPoint)
                    // Only add segment if it's at least 1 meter long
                    if (segLength >= 1.0) {
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
            if (segLength >= 1.0) {
                segments.add(Pair(segmentStart, lastValidPoint))
            }
        }

        // If no segments found but all points were valid, return original line
        return if (segments.isEmpty() && pointsAlongLine.all { it.second }) {
            listOf(Pair(start, end))
        } else if (segments.isEmpty()) {
            // Line entirely inside obstacle - return empty
            emptyList()
        } else {
            segments
        }
    }

    /**
     * Expand a polygon by a given distance (buffer) using proper offset algorithm
     * Each edge is moved outward by the buffer distance, and new vertices are calculated
     * at the intersection of offset edges
     *
     * @param polygon Original polygon vertices (should be in order)
     * @param bufferMeters Buffer distance in meters
     * @return Expanded polygon
     */
    private fun expandPolygon(polygon: List<LatLng>, bufferMeters: Double): List<LatLng> {
        if (polygon.size < 3 || bufferMeters <= 0) return polygon

        // First, ensure polygon is ordered clockwise
        val orderedPolygon = GridUtils.orderPointsClockwise(polygon)
        val n = orderedPolygon.size
        val expanded = mutableListOf<LatLng>()

        for (i in 0 until n) {
            val prev = orderedPolygon[(i - 1 + n) % n]
            val curr = orderedPolygon[i]
            val next = orderedPolygon[(i + 1) % n]

            // Calculate edge directions for adjacent edges
            // Direction from prev to curr
            val dx1 = curr.longitude - prev.longitude
            val dy1 = curr.latitude - prev.latitude
            val len1 = sqrt(dx1 * dx1 + dy1 * dy1)

            // Direction from curr to next
            val dx2 = next.longitude - curr.longitude
            val dy2 = next.latitude - curr.latitude
            val len2 = sqrt(dx2 * dx2 + dy2 * dy2)

            if (len1 == 0.0 || len2 == 0.0) {
                expanded.add(curr)
                continue
            }

            // Normalize the direction vectors
            val nx1 = dx1 / len1
            val ny1 = dy1 / len1
            val nx2 = dx2 / len2
            val ny2 = dy2 / len2

            // Outward normals (perpendicular, pointing outward for clockwise polygon)
            // For edge 1 (prev->curr): normal points to the right
            val outNx1 = ny1
            val outNy1 = -nx1
            // For edge 2 (curr->next): normal points to the right
            val outNx2 = ny2
            val outNy2 = -nx2

            // Average of the two outward normals (bisector direction)
            var bisectX = outNx1 + outNx2
            var bisectY = outNy1 + outNy2
            val bisectLen = sqrt(bisectX * bisectX + bisectY * bisectY)

            if (bisectLen < 0.001) {
                // Edges are nearly parallel, use one normal
                bisectX = outNx1
                bisectY = outNy1
            } else {
                bisectX /= bisectLen
                bisectY /= bisectLen
            }

            // Calculate the offset distance along the bisector
            // For a corner, we need to move further along the bisector to maintain buffer distance from edges
            val dotProduct = outNx1 * bisectX + outNy1 * bisectY
            val offsetMultiplier = if (dotProduct > 0.1) 1.0 / dotProduct else 1.0

            // Convert buffer from meters to degrees (approximate)
            // 1 degree latitude ≈ 111,111 meters
            // 1 degree longitude ≈ 111,111 * cos(lat) meters
            val bufferLat = bufferMeters / 111111.0
            val bufferLng = bufferMeters / (111111.0 * cos(Math.toRadians(curr.latitude)))

            // Apply offset
            val newLat = curr.latitude + bisectY * bufferLat * offsetMultiplier
            val newLng = curr.longitude + bisectX * bufferLng * offsetMultiplier

            expanded.add(LatLng(newLat, newLng))
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
     * @param center Center point of the survey
     * @param width Width in meters
     * @param height Height in meters
     * @param params Grid parameters
     * @return GridSurveyResult
     */
    fun generateRectangularSurvey(
        center: LatLng,
        width: Double,
        height: Double,
        params: GridSurveyParams
    ): GridSurveyResult {
        // Create rectangular polygon
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
     * @param polygon Survey area
     * @return Optimal angle in degrees
     */
    fun calculateOptimalGridAngle(polygon: List<LatLng>): Float {
        if (polygon.size < 3) return 0f

        val longestSideAngle = GridUtils.getAngleOfLongestSide(polygon)
        // Align grid perpendicular to longest side for maximum efficiency
        return ((longestSideAngle + 90) % 360).toFloat()
    }

    /**
     * Estimate coverage area for given parameters
     * @param polygon Survey area
     * @param lineSpacing Line spacing in meters
     * @return Coverage percentage (0-100)
     */
    fun estimateCoverage(polygon: List<LatLng>, lineSpacing: Float): Float {
        val area = GridUtils.calculatePolygonArea(polygon)
        if (area <= 0) return 0f

        // Simple estimation based on line spacing
        // This is a rough approximation - real coverage depends on sensor width
        val estimatedCoveredArea = area * (1.0 - lineSpacing / 100.0) // Simplified model
        return (estimatedCoveredArea / area * 100).coerceIn(0.0, 100.0).toFloat()
    }
}
