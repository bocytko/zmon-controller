var search = element(by.model('alertFilter'));

exports.searchAlert = function(input, cb) {
    search.clear();
    search.sendKeys(input).then(function() {
        browser.driver.sleep(1000);
        browser.findElements(by.repeater("def in alertDefinitionsByStatus | filter: alertFilter | orderBy:'name'"))
            .then(cb);
    });
};

exports.filterTeam = function(cb) {
    var teamMenu = element(by.css('.team-filter a.dropdown-toggle'));
    teamMenu.click().then(function() {
        browser.findElements(by.css('.team-filter a.dropdown-toggle')).then(cb);
    });
};
