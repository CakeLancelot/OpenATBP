var unity;

function isInternetExplorer() {
  if (window.document.documentMode) {
    return true;
  }
  return false;
}

function createParam(name, val) {
  var param = document.createElement('param');
  param.setAttribute('name', name)
  param.setAttribute('value', val)
  return param
}

function embedUnity() {
  var object = document.createElement('object');
  object.setAttribute('class', "embed-responsive embed-responsive-16by9");
  object.setAttribute('classid', "clsid:444785F1-DE89-4295-863A-D46C3A781394");
  object.setAttribute('codebase', "undefined/UnityWebPlayer.cab#version=2,0,0,0");
  object.setAttribute('id', "unity-object");
  object.setAttribute('width', "100%");
  object.setAttribute('height', "100%");

  var params = {
    'src': "CNChampions.unity3d",
    'bordercolor': "DF2900",
    'backgroundcolor': "021E2F",
    'textcolor': "DF2900",
    'disableContextMenu': "true",
    'disablefullscreen': "false",
    'logoimage': "logoimage.png",
    'progressbarimage': "progressbarimage.png",
    'progressframeimage': "progressframeimage.png"
  }

  if(!isInternetExplorer()) {
    var embed = document.createElement('embed');
    embed.setAttribute('class', "embed-responsive-item");
    embed.setAttribute('type', "application/vnd.unity");
    embed.setAttribute('id', "unity-embed");
    Object.keys(params).forEach(function(key) {
      embed.setAttribute(key, params[key]);
    });
  } else {
    Object.keys(params).forEach(function(key) {
      var paramToAppend = createParam(key, params[key])
      object.appendChild(paramToAppend);
    });
  }

  var div = document.getElementById("embed-container");
  div.innerHTML = '';
  if(!isInternetExplorer()) {object.appendChild(embed)};
  div.appendChild(object);
  unity = document.getElementById('unity-object');
}

window.onload = function () {
  if(!isInternetExplorer()) {
    for (var i = 0;i < navigator.plugins.length;i++) {
      if (navigator.plugins[i].name.indexOf("Unity Player") != -1) {
        embedUnity();
      }
    }
  } else {
    try {
      var plugin = new ActiveXObject('UnityWebPlayer.UnityWebPlayer.1');
      embedUnity();
    } catch (e) {
      console.log("Failed to embed ActiveXObject")
    }
  }
};

function Fireteam_CheckMSIBLoggedIn(name, callback) {
  /*
  returnOK = document.cookie
    .split('; ')
    .find((row) => row.startsWith('logged'))
    ?.split('=')[1];
  console.log(returnOK);
  if (returnOK == undefined) returnOK = 'false';
  unity.SendMessage(name, callback, returnOK);*/
}

function Fireteam_GetCookies(name, callback) {
  unity.SendMessage(name, callback, document.cookie);
}

function Fireteam_AspenInit(name, callback) {
  // stubbed
}

var Fireteam_AspenSend = function (name, callback) {
  console.log('Fireteam!');
  console.log(name);
  console.log(callback);
};

function AchievementUnityComm() {}

AchievementUnityComm.doUnityLoaded = function () {
  // stubbed
};

AchievementUnityComm.doUnityGameStarted = function () {
  // stubbed
};

function TopScoresModuleComm() {}

TopScoresModuleComm.onScore = function () {
  // stubbed
};

var UnityRequest = function (
  go_name,
  responce_func,
  request_name,
  request_param
) {
  alert('llego msg!' + 'obj: ' + go_name + '.' + responce_func + '()');
  alert(request_name);
  if (request_name == 'isLoggedIn') {
    unity.SendMessage(go_name, responce_func, 'LOGGED');
  } else if (request_name == 'checkAuthorization') {
    unity.SendMessage(go_name, responce_func, 'AUTHORIZED');
  } else if (request_name == 'readCookie') {
    unity.SendMessage(go_name, responce_func, '');
  }
};

var LoginModule = function () {};

LoginModule.showLoginWindow = function () {
/*  if (true) {
    //TODO: Fix so it is variable
    window.location.href =
      'https://discord.com/api/oauth2/authorize?client_id=' +
      client_id +
      '&redirect_uri=' +
      redirect +
      '&response_type=code&scope=identify';
  } else {
    console.log(discord_enabled);
    var userName = prompt('Enter name');
    if (userName != null) {
      try {
        fetch(`service/authenticate/user/${userName.replace('%20', '')}`, {
          //If user exists, requests the user information to authenticate
          Method: 'GET',
          Body: JSON.stringify({ username: userName }),
          Mode: 'cors',
          Cache: 'default',
        })
          .then((res) => res.json())
          .then((data) => {
            if (data != null) {
              (document.cookie = `TEGid=${data.user.TEGid}`),
                (document.cookie = `authid=${data.user.authid}`);
              document.cookie = `dname=${data.user.dname}`;
              document.cookie = `authpass=${data.user.authpass}`;
              document.cookie = 'logged=true';
              unity.SendMessage('MainGameController', 'HandleLogin', '');
            } else {
              //If user does not exist, it asks for a password to add as the authpass to the database. If passwords are stored, will need to hash
              var password = prompt('New user! Enter password');
              if (password != null) {
                var xhr = new XMLHttpRequest();
                xhr.open(
                  'POST',
                  `service/authenticate/user/${userName.replace('%20', ' ')}`
                );

                xhr.onload = () => {
                  var userData = JSON.parse(xhr.response);

                  (document.cookie = `TEGid=${userData.TEGid}`),
                    (document.cookie = `authid=${userData.authid}`);
                  document.cookie = `dname=${userData.dname}`;
                  document.cookie = `authpass=${userData.authpass}`;
                  document.cookie = 'logged=true';
                  unity.SendMessage('MainGameController', 'HandleLogin', '');
                };

                xhr.send(JSON.stringify({ password: password }));
              }
            }
          })
          .catch((err) => {
            console.log(err);
          });
      } catch (e) {
        console.log(e);
      }
    }
  }*/
};
