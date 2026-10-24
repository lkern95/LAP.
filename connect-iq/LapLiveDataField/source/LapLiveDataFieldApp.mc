using Toybox.Application;
using Toybox.WatchUi;

class LapLiveDataFieldApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        return [ new LapLiveDataField() ];
    }
}
