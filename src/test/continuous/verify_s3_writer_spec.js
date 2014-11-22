var agent = require('superagent');
var async = require('async');
var moment = require('moment');
var testName = __filename;
var hubUrl = process.env.hubUrl || 'hub-v2.svc.dev';
hubUrl = 'http://' + hubUrl + '/channel';
console.log(hubUrl);

var timeout = 60 * 1000;
var minute_format = '/YYYY/MM/DD/HH/mm';
var startOffset = 5;
var endOffset = 40;

/**
 * This should load all the channels in the hub.
 */
describe(testName, function () {

    var channels = [];
    var channelTimes = [];

    it('loads channels', function (done) {
        agent
            .get(hubUrl)
            .set('Accept', 'application/json')
            .end(function (res) {
                expect(res.error).toBe(false);
                channels = res.body._links.channels;
                done();
            })
    });

    it('cross product of channels and times', function () {
        for (var i = startOffset; i <= endOffset; i++) {
            var start = moment.utc().subtract(i, 'minutes');
            var formatted = start.format(minute_format);
            channels.forEach(function (channel) {
                channelTimes.push({url : channel.href + formatted});
            });
        }
    });


    it('compares query results', function (done) {
        async.eachLimit(channelTimes, 10,
            function (channelTime, callback) {
                async.parallel([
                        function (callback) {
                            agent
                                .get(channelTime.url + '?location=CACHE')
                                .set('Accept', 'application/json')
                                .end(function (res) {
                                    expect(res.error).toBe(false);
                                    callback(null, res.body._links.uris);
                                });
                        },
                        function (callback) {
                            agent
                                .get(channelTime.url + '?location=LONG_TERM')
                                .set('Accept', 'application/json')
                                .end(function (res) {
                                    expect(res.error).toBe(false);
                                    callback(null, res.body._links.uris);
                                });
                        }
                    ],
                    function (err, results) {
                        console.log(channelTime.url + ' ' + results[0].length + ' ' + results[1].length);
                        expect(results[0].length - results[1].length).toBe(0);
                        callback(err);
                    });

            }, function (err) {
                done(err);
            });

    }, timeout);

    //todo - gfm - 11/21/14 - this test could also compare payloads


});
