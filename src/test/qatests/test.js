var chai = require('chai');
var expect = chai.expect;
var assert = require('chai').assert;
var superagent = require('superagent');
var crypto = require('crypto');
var request = require('request');
var moment = require('moment');
var async = require('async');
var fs = require('fs');
var http = require('http');


var testRandom = require('./js_testing_utils/randomUtils.js');


// DH Content Types
var appContentTypes = require('./js_testing_utils/contentTypes.js').applicationTypes;
var imageContentTypes = require('./js_testing_utils/contentTypes.js').imageTypes;
var messageContentTypes = require('./js_testing_utils/contentTypes.js').messageTypes;
var textContentTypes = require('./js_testing_utils/contentTypes.js').textTypes;


var URL_ROOT = 'http://datahub-01.cloud-east.dev:8080';
var CAT_TOILET_PIC = './artifacts/cattoilet.jpg';
var MY_2MB_FILE = './artifacts/Iam2_5Mb.txt';
var MY_2KB_FILE = './artifacts/Iam200kb.txt';



// Test variables that are regularly overwritten
var agent
    , payload
    , spamChannelNames
    , fileAsAStream
    , req
    , uri
    , verAgent
    , contentType;

var channelName = testRandom.randomString(Math.round(Math.random() * 48), testRandom.limitedRandomChar);





before(function(myCallback){
    agent = superagent.agent();
    makeChannel(channelName, function(res){
        if ((res.error) || (res.status != 200)) {
            myCallback(res.error);
        };
        console.log('Main test channel:'+ channelName);
        myCallback();
    });
});

beforeEach(function(){
    agent = superagent.agent();
    payload = uri = req = contentType = '';
})

// Helpers
var getValidationString = function (myUri, myPayload, myDone)
{
    var myData = '';
    http.get(myUri, function(res) {
        res.on('data', function (chunk) {
            myData += chunk;
        }).on('end', function(){
                expect(myData).to.equal(myPayload);
                myDone();
            });
    }).on('error', function(e) {
            console.log("Got error: " + e.message);
            myDone();
        });
};


var getValidationChecksum = function (myUri, expChecksum, myDone)
{
    var md5sum = crypto.createHash('md5');
    var actChecksum;

    http.get(myUri, function(res) {
        res.on('data', function (chunk) {
            md5sum.update(chunk);
        }).on('end', function(){
                actChecksum = md5sum.digest('hex');
                expect(actChecksum).to.equal(expChecksum);
                myDone();
            });
        }).on('error', function(e) {
            console.log("Got error: " + e.message);
            myDone();
        });

};


var makeChannel = function(myChannelName, myCallback) {

    var myPayload = '{"name":"'+ myChannelName +'"}';

    //console.log('POSTING to '+ URL_ROOT +'/channel');
    //console.log('With payload:'+ myPayload);

    agent.post(URL_ROOT +'/channel')
        .set('Content-Type', 'application/json')
        .send(myPayload)
        .end(function(err, res) {
            //console.log('\nCreated channel with name:'+ myChannelName);
            myCallback(res);
        }).on('error', function(e) {
            myCallback(e);
        });
}

// Returns GET response in callback
var postDataAndConfirmContentType = function(myChannelName, myContentType, myCallback) {

    payload = testRandom.randomString(Math.round(Math.random() * 50));
    uri = URL_ROOT +'/channel/'+ myChannelName;
    var getAgent = superagent.agent();

    agent.post(uri)
        .set('Content-Type', myContentType)
        .send(payload)
        .end(function(err, res) {
            if (err) throw err;
            expect(res.status).to.equal(200);
            uri = res.body._links.self.href;

            getAgent.get(uri)
                .end(function(err2, res2) {
                    if (err2) throw err2;
                    expect(res2.type.toLowerCase()).to.equal(myContentType.toLowerCase());
                    myCallback(res2);
                });


        });
};


describe('Create Channel', function(){

   // 404 trying to GET channel before it exists
    it('should return a 404 trying to GET channel before it exists', function(done){
        agent.get(URL_ROOT +'/channel/'+ testRandom.randomString(Math.round(Math.random() * 48), testRandom.limitedRandomChar))
            .set('Content-Type', 'application/json')
            .end(function(err, res) {
                expect(res.status).to.equal(404);
                done();
            });
    });


    it('Cannot create channel with blank name: 500 repsonse', function(done){
       makeChannel('', function(res) {
            expect(res.status).to.equal(500);
            done();
        });

    });


    it('should return a 200 trying to GET channel after creation', function(done){

        agent.get(URL_ROOT +'/channel/'+ channelName)
            .set('Content-Type', 'application/json')
            .end(function(err, res) {
                expect(res.status).to.equal(200);
                done();
            });

    });

    // TODO: sequence:  create channel, post data to it, confirm data is there, create channel with same name again,
    //      see if data is still available.

});



// Tests for this story:
//  Allow a client to save an arbitrary value to a channel.
//  https://www.pivotaltracker.com/story/show/43221623


describe('POST data to channel', function(){


    it('Acceptance - should return a 200 for POSTing data', function(done){

        payload = testRandom.randomString(Math.round(Math.random() * 50));
        uri = URL_ROOT +'/channel/'+ channelName;

        agent.post(uri)
            //.set('Content-Type', 'text/plain')
            .send(payload)
            .end(function(err, res) {
                if (err) throw err;
                expect(res.status).to.equal(200);
                uri = res.body._links.self.href;

                getValidationString(uri, payload, done);

             });

    });


    it('POST should return a 404 trying to save to nonexistent channel', function(done){
        payload = testRandom.randomString(Math.round(Math.random() * 50));
        uri = URL_ROOT +'/channel/'+ testRandom.randomString(Math.round(Math.random() * 30), testRandom.limitedRandomChar);
        agent = superagent.agent();

        agent.post(uri)
            .set('Content-Type', 'application/json')
            .send(payload)
            .end(function(err, res) {
                if (err) throw err;
                expect(res.status).to.equal(404);

                done();
            });

    });


    it('POST same set of data twice to channel', function(done){
        payload = testRandom.randomString(Math.round(Math.random() * 50));
        uri = URL_ROOT +'/channel/'+ channelName;
        agent.post(uri)
            .send(payload)
            .end(function(err, res) {
                if (err) throw err;
                expect(res.status).to.equal(200);



                var myAgent = superagent.agent();
                myAgent.post(uri)
                    .send(payload)
                    .end(function(err2, res2){
                        if (err2) throw err2;
                        expect(res2.status).to.equal(200);
                        uri = res.body._links.self.href

                        getValidationString(uri, payload, done);
                    });
            });
    });

    it('POST same set of data to two different channels', function(done) {
        var otherChannelName = testRandom.randomString(Math.round(Math.random() * 30), testRandom.limitedRandomChar);

        makeChannel(otherChannelName, function(res) {
            expect(res.status).to.equal(200);
            expect(res.body._links.self.href).to.equal(URL_ROOT +'/channel/'+ otherChannelName);

            payload = testRandom.randomString(Math.round(Math.random() * 50));
            uri = URL_ROOT +'/channel/'+ channelName;
            agent.post(uri)
                .send(payload)
                .end(function(err, res) {
                    if (err) throw err;
                    expect(res.status).to.equal(200);
                    var actualUri = res.body._links.self.href;

                    getValidationString(actualUri, payload, function() {

                        uri = URL_ROOT +'/channel/'+ otherChannelName;
                        var agent2 = superagent.agent();
                        agent2.post(uri)
                            .send(payload)
                            .end(function(err2, res2) {
                                if (err2) throw err2;

                                expect(res2.status).to.equal(200);
                                actualUri = res2.body._links.self.href;

                                getValidationString(actualUri, payload, done)
                            });

                    });
                });
        });


    });


    it('POST empty data set to channel', function(done){
        payload = '';
        uri = URL_ROOT +'/channel/'+ channelName;
        agent.post(uri)
            .send(payload)
            .end(function(err, res) {
                if (err) throw err;
                expect(res.status).to.equal(200);
                uri = res.body._links.self.href;

                getValidationString(uri, payload, done);

            });
    });

    it('POST 200kb file to channel', function(done) {
        uri = URL_ROOT +'/channel/'+ channelName;

        payload = fs.readFileSync(MY_2KB_FILE, "utf8");
        agent.post(uri)
            .send(payload)
            .end(function(err, res) {
                expect(res.status).to.equal(200);
                uri = res.body._links.self.href;

                getValidationString(uri, payload, done);
            });
    });

    it('POST 1,000 characters to channel', function(done) {
        uri = URL_ROOT +'/channel/'+ channelName;

        payload = testRandom.randomString(1000, testRandom.simulatedTextChar);

        agent.post(uri)
            .send(payload)
            .end(function(err, res) {
                expect(res.status).to.equal(200);
                uri = res.body._links.self.href;

                getValidationString(uri, payload, done);
            });
    });

    // Confirms via md5 checksum
    it('POST image file to channel and recover', function(done) {
        uri = URL_ROOT +'/channel/'+ channelName;

        fileAsAStream = fs.createReadStream(CAT_TOILET_PIC);

        fileAsAStream.pipe(request.post(uri,
            function(err, res, body) {
                if (err) {
                    throw err;
                }
                expect(res.statusCode).to.equal(200);
                uri = JSON.parse(body)._links.self.href;
                //console.log(uri);

                var md5sum = crypto.createHash('md5');
                var s = fs.ReadStream(CAT_TOILET_PIC);

                s.on('data', function(d) {
                    md5sum.update(d);
                }).on('end', function() {
                    var expCheckSum = md5sum.digest('hex');

                    getValidationChecksum(uri, expCheckSum, done);
                });

            })
        );

    });


    describe.skip('Load tests - POST data', function(){

        var loadChannels = {};
        var loadChannelKeys = [];  // channel.uri (to fetch data) and channel.data, e.g. { con {uri: x, data: y}}

        // To ignore the Loadtest cases:  mocha -R nyan --timeout 4000 --grep Load --invert
        it('Loadtest - POST rapidly to five different channels, then confirm data retrieved via GET is correct', function(done){
            for (var i = 1; i <= 20; i++)
            {
                var thisName = testRandom.randomString(Math.round(Math.random() * 48), testRandom.limitedRandomChar);
                var thisPayload = testRandom.randomString(Math.round(Math.random() * 50));
                loadChannels[thisName] = {"uri":'', "data":thisPayload};

            }

            for (var x in loadChannels){
                if (loadChannels.hasOwnProperty(x)) {
                    loadChannelKeys.push(x);
                }
            }

            async.each(loadChannelKeys, function(cn, callback) {
                makeChannel(cn, function(res) {
                    expect(res.status).to.equal(200);
                    expect(res.body._links.self.href).to.equal(URL_ROOT +'/channel/'+ cn);
                    callback();
                });

            }, function(err) {
                if (err) {
                    throw err;
                };

                async.each(loadChannelKeys, function(cn, callback) {
                    agent = superagent.agent();
                    agent.post(URL_ROOT +'/channel/'+ cn)
                        .send(loadChannels[cn].data)
                        .end(function(err, res) {
                            expect(res.status).to.equal(200);
                            loadChannels[cn].uri = res.body._links.self.href;
                            callback();
                        });
                }, function(err) {
                    if (err) {
                        throw err;
                    };

                    async.eachSeries(loadChannelKeys, function(cn, callback) {
                        uri = loadChannels[cn].uri;
                        payload = loadChannels[cn].data;
                        getValidationString(uri, payload, function(){
                            console.log('Confirmed data retrieval from channel: '+ cn);
                            callback();
                        });
                    }, function(err) {
                        if (err) {
                            throw err;
                        };

                        done();
                    });

                });

            });

        });
    });


    // For story:  Provide the client with a creation-timestamp in the response from a data storage request.k
    // https://www.pivotaltracker.com/story/show/43221779

    describe('POST - Creation timestamps returned', function() {

        it('Creation timestamp returned on data storage', function(done){

            var timestamp = '';

            payload = testRandom.randomString(Math.round(Math.random() * 50));
            uri = URL_ROOT +'/channel/'+ channelName;

            agent.post(uri)
                .send(payload)
                .end(function(err, res) {
                    if (err) throw err;
                    expect(res.status).to.equal(200);
                    timestamp = moment(res.body.timestamp);

                    //console.log('timestamp: '+ timestamp);
                    expect(moment(timestamp).isValid()).to.be.true;

                   done();
                });

        });

        it('Multiple POSTings of data to a channel should return ever-increasing creation timestamps.', function(done) {
            var respMoment;

            // will be set to the diff between now and initial response time plus five minutes, just to ensure there
            //      aren't any egregious shenanigans afoot. In milliseconds.
            var serverTimeDiff;

            async.waterfall([
                function(callback){
                    setTimeout(function(){
                        payload = testRandom.randomString(testRandom.randomNum(51));
                        uri = URL_ROOT +'/channel/'+ channelName;

                        agent.post(uri)
                            .send(payload)
                            .end(function(err, res) {
                                if (err) throw err;
                                expect(res.status).to.equal(200);
                                respMoment = moment(res.body.timestamp);
                                //console.log('Creation time was: '+ respMoment.format('X'));

                                expect(respMoment.isValid()).to.be.true;
                                serverTimeDiff = moment().diff(respMoment) + 300000;

                                callback(null, respMoment);
                            });
                    }, 1000);
                }
                ,function(lastResp, callback){
                    setTimeout(function(){
                        payload = testRandom.randomString(testRandom.randomNum(51));

                        agent.post(uri)
                            .send(payload)
                            .end(function(err, res) {
                                if (err) throw err;
                                expect(res.status).to.equal(200);
                                respMoment = moment(res.body.timestamp);
                                //console.log('Creation time was: '+ respMoment.format('X'));

                                expect(respMoment.isValid()).to.be.true;
                                expect(respMoment.isAfter(lastResp)).to.be.true;
                                expect(moment().diff(respMoment)).to.be.at.most(serverTimeDiff);

                                callback(null, respMoment);
                            });
                    }, 1000);

                }
                ,function(lastResp, callback){
                    setTimeout(function(){

                        payload = testRandom.randomString(testRandom.randomNum(51));

                        agent.post(uri)
                            .send(payload)
                            .end(function(err, res) {
                                if (err) throw err;
                                expect(res.status).to.equal(200);
                                respMoment = moment(res.body.timestamp);
                                //console.log('Creation time was: '+ respMoment.format('X'));

                                expect(respMoment.isValid()).to.be.true;
                                expect(respMoment.isAfter(lastResp)).to.be.true;
                                expect(moment().diff(respMoment)).to.be.at.most(serverTimeDiff);

                                callback(null);
                            });
                    }, 1000);

                }

            ]
             ,function(err) {
                    if (err) throw err;
                    done();
             });
        });


        // TODO: POST data from different timezone and confirm timestamp is correct?




    });


});


// Provide a client with the content type when retrieving a value. https://www.pivotaltracker.com/story/show/43221431
describe('GET data -- content type is returned in response', function() {
    // (acceptance)  Submit a request to save some data with a specified content type (image/jpeg, for example).
    //          Verify that the same content type is returned when retrieving the data.
    // Test where specified content type doesn't match actual content type (shouldn't matter, the DH should return specified content type).
    // Test with a range of content types.

    it('Acceptance - Content Type that was specified when POSTing data is returned on GET', function(done){

        postDataAndConfirmContentType(channelName, 'text/plain', function(res) {
            done();
        });

    });

    // application Content-Types
    it('Content-Type for application/*', function(done){
        async.each(appContentTypes, function(ct, nullCallback) {
            //console.log('CT: '+ ct);
            postDataAndConfirmContentType(channelName, ct, function(res) {
                nullCallback();
            });
        }, function(err) {
                if (err) {
                    throw err;
                };
                done();
        });
    });

    // image Content-Types
    it('Content-Type for image/*', function(done){
        async.each(imageContentTypes, function(ct, nullCallback) {
            //console.log('CT: '+ ct);
            postDataAndConfirmContentType(channelName, ct, function(res) {
                nullCallback();
            });
        }, function(err) {
            if (err) {
                throw err;
            };
            done();
        });
    });

    // message Content-Types
    it('Content-Type for message/*', function(done){
        async.each(messageContentTypes, function(ct, nullCallback) {
            //console.log('CT: '+ ct);
            postDataAndConfirmContentType(channelName, ct, function(res) {
                nullCallback();
            });
        }, function(err) {
            if (err) {
                throw err;
            };
            done();
        });
    });

    // text Content-Types
    it('Content-Type for textContentTypes/*', function(done){
        async.each(textContentTypes, function(ct, nullCallback) {
            //console.log('CT: '+ ct);
            postDataAndConfirmContentType(channelName, ct, function(res) {
                nullCallback();
            });
        }, function(err) {
            if (err) {
                throw err;
            };
            done();
        });
    });

});


describe.skip('Known failing cases that may not be legitimate', function() {

    it('Should be able to create a channel name of 50 characters', function(done){
        var longChannelName = testRandom.randomString(50, testRandom.limitedRandomChar);

        //console.log('Channel Name: '+ longChannelName);
        payload = '{"name":"'+ longChannelName +'"}';

        agent.post(URL_ROOT +'/channel')
            .set('Content-Type', 'application/json')
            .send(payload)
            .end(function(err, res) {
                expect(res.status).to.equal(200);
                expect(res.body._links.self.href).to.equal(URL_ROOT +'/channel/'+ channelName);
                done();
            });
    });


    // !!!! this is stopping / hosing the server...do not run w/o checking with devs first
    it('POST large file (2.5 MB) to channel', function(done) {
        uri = URL_ROOT +'/channel/'+ channelName;

        payload = fs.readFileSync(MY_2MB_FILE);

        agent.post(uri)
            .send(payload)
            .end(function(err, res) {
                expect(res.status).to.equal(200);
                uri = res.body._links.self.href;

                console.log('URI:'+ uri);

                getValidationString(uri, payload, done);
            });
    });
});




// TODO:  stress tests -- handled elsewhere?



