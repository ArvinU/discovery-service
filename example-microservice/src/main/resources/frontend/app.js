(function () {
    function update() {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', './api/status');
        xhr.onload = function () {
            if (xhr.status === 200) {
                var data = JSON.parse(xhr.responseText);
                document.getElementById('instanceId').textContent = data.instanceId || '--';
                document.getElementById('instanceBadge').textContent = data.instanceId || '--';
                document.getElementById('port').textContent = data.port || '--';
                document.getElementById('uptime').textContent = data.uptimeFormatted || '--';
                document.getElementById('pid').textContent = data.pid || '--';
                document.getElementById('javaVersion').textContent = data.javaVersion || '--';
                document.getElementById('cpus').textContent = data.availableProcessors || '--';
                document.getElementById('maxMem').textContent = (data.maxMemoryMB || '--') + ' MB';
                document.getElementById('freeMem').textContent = (data.freeMemoryMB || '--') + ' MB';
                document.getElementById('lastUpdated').textContent = new Date().toLocaleTimeString();
            } else {
                document.getElementById('lastUpdated').textContent =
                    'HTTP ' + xhr.status + ' — ' + new Date().toLocaleTimeString();
            }
        };
        xhr.onerror = function () {
            document.getElementById('lastUpdated').textContent = 'Error - ' + new Date().toLocaleTimeString();
        };
        xhr.send();
    }

    update();
    setInterval(update, 3000);
})();
