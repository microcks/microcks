function(key, values){ 
   var stat={'dailyCount':0, 'hourlyCount':{'0':0,'1':0,'2':0,'3':0,'4':0,'5':0,'6':0,'7':0,'8':0,'9':0,'10':0,'11':0,'12':0,'13':0,'14':0,'15':0,'16':0,'17':0,'18':0,'19':0,'20':0,'21':0,'22':0,'23':0}};
   for (var i=0;i<values.length;i++) {
      stat.dailyCount += values[i].dailyCount;
      for (var j=0;j<24;j++) {
         stat.hourlyCount[j.toString().split('.')[0]] += values[i].hourlyCount[j.toString().split('.')[0]];
      }
   } 
   return stat; 
}