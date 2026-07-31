package com.xrdesk

/**
 * Shared logic for JS-to-Native video detection bridge.
 */
object VideoDetectionBridge {

    fun getInjectionScript(): String {
        return """
            (function() {
                if (window.VideoDetectorInjected) return;
                window.VideoDetectorInjected = true;

                function notifyAndroid(el, source, category) {
                    var url = el.currentSrc || el.src;
                    if (!url || url.length < 5) return;
                    var data = {
                        url: url,
                        type: el.type || 'video/unknown',
                        source: source,
                        category: category || 'HTML_VIDEO',
                        pageUrl: window.location.href,
                        dimensions: el.videoWidth + 'x' + el.videoHeight,
                        duration: el.duration ? el.duration.toFixed(1) + 's' : 'unknown'
                    };
                    
                    var json = JSON.stringify(data);
                    if (window.VideoDetector) window.VideoDetector.onVideoFound(json);
                    if (window.VideoResolver) window.VideoResolver.onVideoFound(json);
                }

                function checkElement(el) {
                    if (el.tagName === 'VIDEO') {
                        notifyAndroid(el, 'video_element', 'HTML_VIDEO');
                        el.addEventListener('loadedmetadata', () => notifyAndroid(el, 'event_loadedmetadata', 'HTML_VIDEO'), { once: true });
                        el.addEventListener('play', () => notifyAndroid(el, 'event_play', 'HTML_VIDEO'));
                    }
                }

                document.querySelectorAll('video').forEach(checkElement);
                
                new MutationObserver((mutations) => {
                    mutations.forEach((m) => {
                        m.addedNodes.forEach((node) => {
                            if (node.nodeType === 1) {
                                if (node.tagName === 'VIDEO') checkElement(node);
                                node.querySelectorAll('video').forEach(checkElement);
                            }
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });

                var originalCreateObjectURL = URL.createObjectURL;
                URL.createObjectURL = function(obj) {
                    var url = originalCreateObjectURL.apply(this, arguments);
                    if (obj instanceof MediaSource || obj instanceof Blob) {
                         var data = {
                             url: url, type: obj.type || 'MediaSource', source: 'blob_hook', category: 'BLOB', pageUrl: window.location.href, dimensions: 'N/A', duration: 'N/A'
                         };
                         var json = JSON.stringify(data);
                         if (window.VideoDetector) window.VideoDetector.onVideoFound(json);
                         if (window.VideoResolver) window.VideoResolver.onVideoFound(json);
                    }
                    return url;
                };
            })();
        """.trimIndent()
    }
}
