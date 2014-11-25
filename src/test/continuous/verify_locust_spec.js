var agent = require('superagent');
var async = require('async');
var moment = require('moment');
var testName = __filename;
var locustUrl = process.env.locustUrl || 'hub-node-tester.cloud-east.dev:8089';
locustUrl = 'http://' + locustUrl + '/stats/';
console.log(locustUrl);

/**
 * This should get the results from locust, log them to the console
 * http://hub-node-tester.cloud-east.dev:8089/stats/requests
 * it should also reset the stats.
 * http://hub-node-tester.cloud-east.dev:8089/stats/reset
 */
describe(testName, function () {

    var results;

    it('loads results', function (done) {
        var url = locustUrl + 'requests';
        console.log('url', url);
        agent
            .get(url)
            .set('Accept', 'application/json')
            .end(function (res) {
                expect(res.error).toBe(false);
                results = JSON.parse(res.text);
                console.log('results', results);
                results.stats.forEach(function (item) {
                    console.log('item ' + item.name + ' ' + item.num_failures);
                    expect(item.num_failures).toBe(0);
                });
                done();
            })
    });

    it('resets stats', function (done) {
        var url = locustUrl + 'reset';
        console.log('url', url);
        agent
            .get(url)
            .set('Accept', 'application/json')
            .end(function (res) {
                expect(res.error).toBe(false);
                expect(res.status).toBe(200);
                done();
            })
    });

});
