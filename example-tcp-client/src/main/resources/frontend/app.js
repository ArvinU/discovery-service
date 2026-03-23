(function () {
    var API = './api/status';
    function tick() {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', API);
        xhr.onload = function () {
            if (xhr.status !== 200) return;
            var d = JSON.parse(xhr.responseText);
            document.getElementById('channel').textContent = d.registrationChannel || '--';
            document.getElementById('instanceId').textContent = d.instanceId || '--';
            document.getElementById('svcPort').textContent = d.port != null ? d.port : '--';
            document.getElementById('uptime').textContent = d.uptimeFormatted || '--';
            document.getElementById('pid').textContent = d.pid || '--';
            document.getElementById('lastUpdated').textContent = new Date().toLocaleTimeString();
        };
        xhr.send();
    }
    tick();
    setInterval(tick, 3000);
})();
