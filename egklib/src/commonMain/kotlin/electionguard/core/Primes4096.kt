package electionguard.core

object Primes4096 {
    val nbits = 4096

    // copied from the ElectionGuard 1.9 spec
    val pStr =
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" +
                "B17217F7D1CF79ABC9E3B39803F2F6AF40F343267298B62D8A0D175B8BAAFA2B" +
                "E7B876206DEBAC98559552FB4AFA1B10ED2EAE35C138214427573B291169B825" +
                "3E96CA16224AE8C51ACBDA11317C387EB9EA9BC3B136603B256FA0EC7657F74B" +
                "72CE87B19D6548CAF5DFA6BD38303248655FA1872F20E3A2DA2D97C50F3FD5C6" +
                "07F4CA11FB5BFB90610D30F88FE551A2EE569D6DFC1EFA157D2E23DE1400B396" +
                "17460775DB8990E5C943E732B479CD33CCCC4E659393514C4C1A1E0BD1D6095D" +
                "25669B333564A3376A9C7F8A5E148E82074DB6015CFE7AA30C480A5417350D2C" +
                "955D5179B1E17B9DAE313CDB6C606CB1078F735D1B2DB31B5F50B5185064C18B" +
                "4D162DB3B365853D7598A1951AE273EE5570B6C68F96983496D4E6D330AF889B" +
                "44A02554731CDC8EA17293D1228A4EF98D6F5177FBCF0755268A5C1F9538B982" +
                "61AFFD446B1CA3CF5E9222B88C66D3C5422183EDC99421090BBB16FAF3D949F2" +
                "36E02B20CEE886B905C128D53D0BD2F9621363196AF503020060E49908391A0C" +
                "57339BA2BEBA7D052AC5B61CC4E9207CEF2F0CE2D7373958D762265890445744" +
                "FB5F2DA4B751005892D356890DEFE9CAD9B9D4B713E06162A2D8FDD0DF2FD608" +
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"

    val qStr = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF43"

    val rStr =
        "100000000000000000000000000000000000000000000000000000000000000BC" +
                "B17217F7D1CF79ABC9E3B39803F2F6AF40F343267298B62D8A0D175B8BAB857A" +
                "E8F428165418806C62B0EA36355A3A73E0C741985BF6A0E3130179BF2F0B43E3" +
                "3AD862923861B8C9F768C4169519600BAD06093F964B27E02D86831231A9160D" +
                "E48F4DA53D8AB5E69E386B694BEC1AE722D47579249D5424767C5C33B9151E07" +
                "C5C11D106AC446D330B47DB59D352E47A53157DE04461900F6FE360DB897DF53" +
                "16D87C94AE71DAD0BE84B647C4BCF818C23A2D4EBB53C702A5C8062D19F5E9B5" +
                "033A94F7FF732F54129712869D97B8C96C412921A9D8679770F499A041C297CF" +
                "F79D4C9149EB6CAF67B9EA3DC563D965F3AAD1377FF22DE9C3E62068DD0ED615" +
                "1C37B4F74634C2BD09DA912FD599F4333A8D2CC005627DCA37BAD43E64A39631" +
                "19C0BFE34810A21EE7CFC421D53398CBC7A95B3BF585E5A04B790E2FE1FE9BC2" +
                "64FDA8109F6454A082F5EFB2F37EA237AA29DF320D6EA860C41A9054CCD24876" +
                "C6253F667BFB0139B5531FF30189961202FD2B0D55A75272C7FD73343F7899BC" +
                "A0B36A4C470A64A009244C84E77CEBC92417D5BB13BF18167D8033EB6C4DD787" +
                "9FD4A7F529FD4A7F529FD4A7F529FD4A7F529FD4A7F529FD4A7F529FD4A7F52A"

    val gStr =
        "36036FED214F3B50DC566D3A312FE4131FEE1C2BCE6D02EA39B477AC05F7F885" +
                "F38CFE77A7E45ACF4029114C4D7A9BFE058BF2F995D2479D3DDA618FFD910D3C" +
                "4236AB2CFDD783A5016F7465CF59BBF45D24A22F130F2D04FE93B2D58BB9C1D1" +
                "D27FC9A17D2AF49A779F3FFBDCA22900C14202EE6C99616034BE35CBCDD3E7BB" +
                "7996ADFE534B63CCA41E21FF5DC778EBB1B86C53BFBE99987D7AEA0756237FB4" +
                "0922139F90A62F2AA8D9AD34DFF799E33C857A6468D001ACF3B681DB87DC4242" +
                "755E2AC5A5027DB81984F033C4D178371F273DBB4FCEA1E628C23E52759BC776" +
                "5728035CEA26B44C49A65666889820A45C33DD37EA4A1D00CB62305CD541BE1E" +
                "8A92685A07012B1A20A746C3591A2DB3815000D2AACCFE43DC49E828C1ED7387" +
                "466AFD8E4BF1935593B2A442EEC271C50AD39F733797A1EA11802A2557916534" +
                "662A6B7E9A9E449A24C8CFF809E79A4D806EB681119330E6C57985E39B200B48" +
                "93639FDFDEA49F76AD1ACD997EBA13657541E79EC57437E504EDA9DD01106151" +
                "6C643FB30D6D58AFCCD28B73FEDA29EC12B01A5EB86399A593A9D5F450DE39CB" +
                "92962C5EC6925348DB54D128FD99C14B457F883EC20112A75A6A0581D3D80A3B" +
                "4EF09EC86F9552FFDA1653F133AA2534983A6F31B0EE4697935A6B1EA2F75B85" +
                "E7EBA151BA486094D68722B054633FEC51CA3F29B31E77E317B178B6B9D8AE0F"

}