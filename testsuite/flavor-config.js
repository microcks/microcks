const baseMocks = [
  'invokeRESTMocks',
  'invokeSOAPMocks',
  'invokeGraphQLMocks',
  'invokeGRPCMocks',
  'invokeREST_HelloAPIMocks',
  'invokeREST_PetStoreAPI',
  //'asyncAPI_websocketMocks',
]
var flavorConfig = {
  'regular-auth': ['ownAPIsAuth'].concat(baseMocks),
  'regular-noauth': ['ownAPIsNoAuth'].concat(baseMocks),
  'uber-jvm': ['ownAPIsNoAuth'].concat(baseMocks),
  'uber-native': ['ownAPIsNoAuth'].concat(
    baseMocks.filter(function (fn) { return fn !== 'invokeSOAPMocks' &&
    fn !== 'invokeREST_HelloAPIMocks'; })
  ),
  // add more flavors as needed…
};
export { flavorConfig };
