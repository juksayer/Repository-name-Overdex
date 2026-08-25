package com.example.overdex.model.navigation

/**
 * Represents a command that can be executed by the instrument terminal.
 */
sealed interface InstrumentCommand {
    data object OpenSearch : InstrumentCommand
    data object OpenCollection : InstrumentCommand
    data object AddSpecimen : InstrumentCommand
    data object OpenBattleHistory : InstrumentCommand
    data object OpenBattleLogs : InstrumentCommand
    data object OpenCapture : InstrumentCommand
    data object OpenCalibration : InstrumentCommand
    data object OpenProfile : InstrumentCommand
    data object OpenTimeline : InstrumentCommand
    data object OpenChat : InstrumentCommand
    data object OpenAccessibilityProbe : InstrumentCommand
    data object OpenSignalObservatory : InstrumentCommand
    data object OpenBattlePreview : InstrumentCommand
    data object OpenMatchSight : InstrumentCommand
    data object OpenMatchCalibration : InstrumentCommand
}
