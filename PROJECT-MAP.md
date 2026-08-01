# Overdex Architecture Inventory

This document provides a complete inventory of Overdex source files related to the project's core architectural vocabulary.

---

## Anchor

**data/observation/**
- [AnchorDetector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/AnchorDetector.kt)
- [SimpleAnchorDetector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/SimpleAnchorDetector.kt)

**model/**
- [AnchorRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/AnchorRegion.kt)

**model/observation/**
- [AnchorObservation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/AnchorObservation.kt)
- [AnchorType.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/AnchorType.kt)

---

## Battle

**app/**
- [BattleMemory.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/BattleMemory.kt)
- [BattleMemoryUpdater.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/BattleMemoryUpdater.kt)
- [BattleState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/BattleState.kt)

**battle/debug/**
- [ObservationPipelineDemo.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/ObservationPipelineDemo.kt)

**battle/debug/accessibility/**
- [AccessibilityProbeManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/accessibility/AccessibilityProbeManager.kt)
- [AccessibilityProbeModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/accessibility/AccessibilityProbeModel.kt)
- [AccessibilityProbeService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/accessibility/AccessibilityProbeService.kt)

**battle/observation/**
- [DroidballService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt)

**battle/timeline/**
- [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt)
- [BattleTimelineBuilder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimelineBuilder.kt)

**battle/timeline/event/**
- [BattleLifecycleEvents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/BattleLifecycleEvents.kt)

**battle/timeline/serialization/**
- [BattleTimelineSerializer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/serialization/BattleTimelineSerializer.kt)

**data/**
- [BattleCalibration.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleCalibration.kt)
- [BattleHistoryRepository.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleHistoryRepository.kt)
- [BattleObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleObserver.kt)

**data/observation/**
- [BattleObservationPipeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/BattleObservationPipeline.kt)

**model/**
- [ArchivedBattle.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/ArchivedBattle.kt)
- [BattleEvent.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleEvent.kt)
- [BattleLifecycleAnalysis.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleLifecycleAnalysis.kt)
- [BattleLog.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleLog.kt)
- [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleTimeline.kt)

**presentation/preview/**
- [BattlePreviewData.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/presentation/preview/BattlePreviewData.kt)

**ui/components/**
- [BattlePanels.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattlePanels.kt)

**ui/screens/**
- [BattleHistoryScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BattleHistoryScreen.kt)
- [BattlePreviewScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BattlePreviewScreen.kt)
- [BattleTimelineScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BattleTimelineScreen.kt)

---

## Calibration

**app/**
- [CalibrationManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CalibrationManager.kt)

**data/**
- [BattleCalibration.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleCalibration.kt)

**ui/components/**
- [CalibrationMode.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/CalibrationMode.kt)
- [CalibrationRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/CalibrationRegion.kt)

**ui/screens/**
- [CalibrationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/CalibrationScreen.kt)

---

## Capture

**app/**
- [CaptureTemplateManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CaptureTemplateManager.kt)

**model/**
- [CaptureRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/CaptureRegion.kt)
- [CaptureTemplate.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/CaptureTemplate.kt)

**model/observation/**
- [CaptureObservation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/CaptureObservation.kt)

**ui/components/**
- [CaptureTemplateOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/CaptureTemplateOverlay.kt)

**ui/screens/**
- [CaptureVerificationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/CaptureVerificationScreen.kt)

---

## Move

**data/observation/**
- [MoveNameRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/MoveNameRecognizer.kt)

**model/**
- [Move.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/Move.kt)

**ui/components/**
- [LiveMoveAnalysisPanel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/LiveMoveAnalysisPanel.kt)

---

## Objective

**model/observation/**
- [ObservationObjective.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationObjective.kt)

---

## Observation

**battle/debug/**
- [ObservationPipelineDemo.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/ObservationPipelineDemo.kt)

**battle/debug/observatory/**
- [ObservationRecorder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/observatory/ObservationRecorder.kt)
- [ObservationRecording.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/observatory/ObservationRecording.kt)

**battle/observation/**
- [Observation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Observation.kt)
- [ObservationDispatcher.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationDispatcher.kt)
- [ObservationReconciler.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationReconciler.kt)
- [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSession.kt)
- [ObservationSessionState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSessionState.kt)
- [BattleWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/BattleWorkspace.kt)

**battle/observation/debug/**
- [ManualObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/debug/ManualObservationSource.kt)
- [ObservationFactory.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/debug/ObservationFactory.kt)

**battle/timeline/event/**
- [ObservationEvent.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/ObservationEvent.kt)

**battle/timeline/observer/**
- [ObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/observer/ObservationSource.kt)

**data/**
- [ObservationCropExtractor.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/ObservationCropExtractor.kt)

**data/observation/**
- [BattleObservationPipeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/BattleObservationPipeline.kt)
- [DroidballObservationInput.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/DroidballObservationInput.kt)
- [GalleryObservationInput.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/GalleryObservationInput.kt)
- [GuidedObservationPipeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/GuidedObservationPipeline.kt)
- [ObservationExtractor.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/ObservationExtractor.kt)
- [ObservationRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/ObservationRecognizer.kt)
- [RecognitionObservationMapper.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/RecognitionObservationMapper.kt)

**model/**
- [Observation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/Observation.kt)
- [ObservationRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/ObservationRegion.kt)

**model/observation/**
- [AnchorObservation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/AnchorObservation.kt)
- [CaptureObservation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/CaptureObservation.kt)
- [Observation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/Observation.kt)
- [ObservationInput.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationInput.kt)
- [ObservationObjective.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationObjective.kt)
- [ObservationResolver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationResolver.kt)
- [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationSession.kt)
- [ObservationSessionState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationSessionState.kt)
- [ObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationSource.kt)

**ui/components/**
- [BattleOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleOverlay.kt)
- [ObservationRegionOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/ObservationRegionOverlay.kt)
- [BattleWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleWorkspace.kt)

**test/model/observation/**
- [ObservationGuidanceTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationGuidanceTest.kt)
- [ObservationIntegrityTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationIntegrityTest.kt)
- [ObservationProgressTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationProgressTest.kt)
- [ObservationResolutionTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationResolutionTest.kt)
- [ObservationSessionAccumulationTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationSessionAccumulationTest.kt)
- [RegistrationObservationFlowTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/RegistrationObservationFlowTest.kt)

**androidTest/validation/**
- [ObservationEngineValidator.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/androidTest/java/com/example/overdex/validation/ObservationEngineValidator.kt)

---

## Observer

**battle/observation/**
- [Observer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Observer.kt)
- [SpeciesObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/SpeciesObserver.kt)

**battle/observation/debug/**
- [DebugObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/debug/DebugObserver.kt)

**battle/timeline/observer/**
- [ObserverId.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/observer/ObserverId.kt)

**data/**
- [BattleObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleObserver.kt)

---

## Overlay

**ui/components/**
- [CaptureTemplateOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/CaptureTemplateOverlay.kt)
- [EnemyTeamOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/EnemyTeamOverlay.kt)
- [BattleOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleOverlay.kt)
- [ObservationRegionOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/ObservationRegionOverlay.kt)

---

## Recognition

**data/observation/**
- [RecognitionObservationMapper.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/RecognitionObservationMapper.kt)

---

## Recognizer

**data/observation/**
- [CandyPanelSpeciesRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/CandyPanelSpeciesRecognizer.kt)
- [CombatPowerRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/CombatPowerRecognizer.kt)
- [MoveNameRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/MoveNameRecognizer.kt)
- [ObservationRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/ObservationRecognizer.kt)
- [ShadowBonusRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/ShadowBonusRecognizer.kt)
- [SpeciesNameRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/SpeciesNameRecognizer.kt)

---

## Region

**model/**
- [AnchorRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/AnchorRegion.kt)
- [CaptureRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/CaptureRegion.kt)
- [ObservationRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/ObservationRegion.kt)

**ui/components/**
- [CalibrationRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/CalibrationRegion.kt)
- [ObservationRegionOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/ObservationRegionOverlay.kt)

---

## Registration

**data/observation/**
- [RegistrationEngine.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/RegistrationEngine.kt)

**model/**
- [RegistrationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/RegistrationSession.kt)
- [RegistrationSessionManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/RegistrationSessionManager.kt)

**model/observation/**
- [RegistrationAssessment.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/RegistrationAssessment.kt)

**test/model/observation/**
- [RegistrationObservationFlowTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/RegistrationObservationFlowTest.kt)

---

## Session

**battle/debug/observatory/**
- [SessionMetadata.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/observatory/SessionMetadata.kt)

**battle/observation/**
- [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSession.kt)
- [ObservationSessionState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSessionState.kt)

**model/**
- [RegistrationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/RegistrationSession.kt)
- [RegistrationSessionManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/RegistrationSessionManager.kt)

**model/observation/**
- [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationSession.kt)
- [ObservationSessionState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/observation/ObservationSessionState.kt)

**ui/components/**
- [BattleWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleWorkspace.kt)

**ui/screens/observatory/**
- [SessionSummaryCard.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/observatory/SessionSummaryCard.kt)

**test/model/observation/**
- [ObservationSessionAccumulationTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/model/observation/ObservationSessionAccumulationTest.kt)

---

## Species

**battle/observation/**
- [SpeciesObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/SpeciesObserver.kt)

**data/**
- [SpeciesJsonLoader.kt](file:///home/sean/AndroidStudioProjects/Overdex/data/SpeciesJsonLoader.kt)

**data/observation/**
- [CandyPanelSpeciesRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/CandyPanelSpeciesRecognizer.kt)
- [SpeciesNameRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/SpeciesNameRecognizer.kt)

**model/**
- [SpeciesData.kt](file:///home/sean/AndroidStudioProjects/Overdex/model/SpeciesData.kt)
- [SpeciesImport.kt](file:///home/sean/AndroidStudioProjects/Overdex/model/SpeciesImport.kt)

---

## Timeline

**battle/timeline/**
- [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt)
- [BattleTimelineBuilder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimelineBuilder.kt)

**battle/timeline/event/**
- [TimelineEvent.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/TimelineEvent.kt)

**battle/timeline/serialization/**
- [BattleTimelineSerializer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/serialization/BattleTimelineSerializer.kt)

**data/**
- [SharedTimelineRepository.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/SharedTimelineRepository.kt)

**model/**
- [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleTimeline.kt)

**ui/screens/**
- [BattleTimelineScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BattleTimelineScreen.kt)
- [SharedTimelineScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt)

**ui/screens/observatory/**
- [TimelineEventRow.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/observatory/TimelineEventRow.kt)
- [TimelineList.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/observatory/TimelineList.kt)

---

## Workspace

**battle/observation/**
- [BattleWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/BattleWorkspace.kt)

**ui/components/**
- [BattleWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleWorkspace.kt)
