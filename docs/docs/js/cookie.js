function getCookie(name) {
    var b = document.cookie.match('(^|[^;]+)\\s*' + name + '\\s*=\\s*([^;]+)');
    return b ? b.pop() : '';
};

window.addEventListener("load", function () {
    var cc = new CookieConsent({
        palette: {
            popup: {
                background: "#000",
                text: "#fff"
            },
            button: {
                background: "#222",  // dark button
                text: "#fff"        // white text
            }
        },
        theme: "classic",
        position: "bottom-right",
        type: "opt-in",
        content: {
            message: "We use cookies to improve your experience and analyze traffic.",
            allow: "Accept",
            dismiss: "Decline",
            link: "Learn more",
            href: "/privacy-policy/"
        },
    })

    cc.on("popupOpened", function () {
        console.log('<em>popupOpened</em> event fired');
    })

    cc.on("popupClose", function () {
        console.log('<em>popupClose</em> event fired');
    })

    cc.on("initialized", function (statuses) {
        log('<em>initialized</em> event fired with statuses: ')
        Object.keys(statuses).forEach(status => {
            log('<em>' + status + ':</em> <em>' + statuses[status] + '</em>');
        })
    })
    cc.on("statusChanged", function (cookieName, status, chosenBefore) {
        log('<em>statusChanged</em> event fired with status <em>' + status + '</em> for cookie <em>' + cookieName + '</em>');
    })
    cc.on("revokeChoice", function () {
        log('<em>revokeChoice()</em> event fired')
    })
    cc.on("error", console.error)

    var cc = window.cookieconsent.initialise({
        palette: {
            popup: {
                background: "#000",
                text: "#fff"
            },
            button: {
                background: "#222",  // dark button
                text: "#fff"        // white text
            }
        },
        theme: "classic",
        position: "bottom-right",
        type: "opt-in",
        content: {
            message: "We use cookies to improve your experience and analyze traffic.",
            allow: "Accept",
            dismiss: "Decline",
            link: "Learn more",
            href: "/privacy-policy/"
        },
        onStatusChange: function (status, chosenBefore) {
            console.log("Cookie status:", status);
            if (status === "allow") {
                loadGTM();
            }
        }
    });

    const popup = document.querySelector('.cc-window');
    if (popup) {
        popup.addEventListener("mouseleave", () => {
            cc.close(); // uses built-in animation
        });
    }
});

function loadGTM() {
    // Insert GTM <script> dynamically
    const cookieStatus = getCookie('cookieconsent_status');
    if (cookieStatus === 'allow') {
        var head = document.getElementsByTagName("head")[0];
        var script = document.createElement("script");
        script.async = true;
        script.src = "https://www.googletagmanager.com/gtm.js?id=GTM-5P98";
        head.appendChild(script);

        // Add <noscript> fallback
        document.getElementById("gtm-noscript").innerHTML = `
        <noscript>
          <iframe src="https://www.googletagmanager.com/ns.html?id=GTM-5P98"
                  height="0" width="0" style="display:none;visibility:hidden"></iframe>
        </noscript>`;
    }
}
